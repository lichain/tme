package com.trendmicro.mist.cmd;

import java.util.HashMap;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.ThreadInvoker;
import com.trendmicro.mist.proto.MistMessage;
import com.trendmicro.mist.proto.MistMessage.MessageBlock;
import com.trendmicro.mist.util.Credential;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.mist.util.BrokerAddress;

import com.google.protobuf.InvalidProtocolBufferException;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

public class MistForwarder extends ThreadInvoker {
    class EnabledLock {
        public UUID lockID = UUID.randomUUID();
        public boolean enabled = false;
    }

    enum RetVal {
        OK, 
        PARSE_ARGUMENT_FAILED, 
        CREATE_SESSION_FAILED, 
        DESTROY_SESSION_FAILED, 
        ATTACH_FAILED, 
        DETACH_FAILED, 
        INVALID_SESSION
    }

    class ForwardTarget {
        public BrokerAddress broker = new BrokerAddress();
        public Credential auth = new Credential();
        public String exchange;
        public boolean determined = false;

        public ForwardTarget(String target) throws MistException {
            try {
                String [] tok = target.split(",");
                if(!(tok.length == 1 || tok.length == 4))
                    throw new MistException(String.format("not valid TARGET `%s'", target));
                if(tok.length == 1)
                    exchange = target;
                else {
                    determined = true;
                    broker.setType(tok[0]);
                    broker.setConnectionList(tok[1].replace(";", ","));
                    auth.set(tok[2]);
                    exchange = tok[3];
                }
            }
            catch(MistException e) {
                throw e;
            }
        }
    }

    /**
     * @author greg_huang
     *
     */
    class MistSinkWorker extends Thread {
        private BlockingQueue<MessageBlock> messageQueue;
        private final MistSink mySink;
        private final int sessID;
        private final int MAX_MSG_BUF_SIZE = 2048;
        private volatile boolean isClose = false;
        private volatile boolean isRunning = false;
        private long dropCount = 0;
        private long deliveryCount =0;

        public MistSinkWorker(ForwardTarget target) throws MistException {
            messageQueue = new ArrayBlockingQueue<MessageBlock>(MAX_MSG_BUF_SIZE);
            sessID = createSession(target);
            if (sessID == -1) 
            	throw new MistException("Can't create a session");
            mySink = new MistSink();
            mySink.setOutputListener(outputListener);
            mySink.setErrorListener(errorListener);
            mySink.invoke(String.format("%d --mount %s", sessID, target.exchange));
            mySink.waitForComplete();
        }

        private synchronized void waitNewMessage(long timeout) {
            try {
                wait(timeout);
            }
            catch(InterruptedException e) {
            }
        }

        @Override
        public void run() {
            if(mySink == null)
                return;

            BufferedOutputStream out = new BufferedOutputStream(mySink.getOutputStream());
            mySink.invoke(sessID + " --attach");
            isRunning = true;

            do {
                if(hasMessage()) {
                    try {
                        Packet out_pack = new Packet();
                        byte[] data = getMessage(100).toByteArray();
                        if(data != null) {
                            out_pack.setPayload(data);
                            out_pack.write(out);
                        }
                    }
                    catch(MistException mie) {
                        myErr.println(mie.getMessage());
                    }
                    catch(IOException ioe) {
                        myErr.println(ioe.getMessage());
                    }
                }
                else {
                    waitNewMessage(100);
                }
                
                deliveryCount = mySink.getMessageCount();
                isRunning = !(isClose && !hasMessage());
                
            } while(isRunning);

            try {
                out.close();
                mySink.getOutputStream().close();
                mySink.invoke(String.format("%d --detach", sessID));
                mySink.getThread().join();
                destroySession(sessID);
            }
            catch(Exception e) {
                exitCode = RetVal.DETACH_FAILED.ordinal();
                myErr.println(e.getMessage());
            }

            isRunning = false;
        }

        public void close() {
            if(isRunning) {
                isClose = true;
                try {
                    this.join();
                }
                catch(InterruptedException e) {
                }
            }
        }

        public boolean hasMessage() {
            return(!messageQueue.isEmpty());
        }

        public boolean canInsertMessage() {
            return(messageQueue.remainingCapacity() > 0);
        }

        private MessageBlock getMessage(int timeout) throws MistException {
            if(timeout > 0) {
                try {
                    return messageQueue.poll(timeout, TimeUnit.MILLISECONDS);
                }
                catch(InterruptedException e) {
                    throw new MistException("MistSinkWorker interrupted");
                }
            }
            else {
                try {
                    return messageQueue.take();
                }
                catch(InterruptedException e) {
                    throw new MistException("MistSinkWorker interrupted");
                }
            }
        }

        public synchronized boolean insertMessage(MessageBlock msg, boolean blockingMode) {
            if(isClose)
                return false;

            if(!blockingMode) {
                try {
                    boolean success = messageQueue.offer(msg, 100, TimeUnit.MILLISECONDS);
                    if(!success) {
                        dropCount++;
                        return false;
                    }
                }
                catch(InterruptedException e) {
                    return false;
                }
            }
            else {
                try {
                    messageQueue.put(msg);
                }
                catch(InterruptedException e) {
                    return false;
                }
            }
            notify();
            return true;
        }

        public int getSessionID() {
            return sessID;
        }

        public long getDropCount() {
            return dropCount;
        }
        
        public long getDeliveryCount() {
        	return deliveryCount;
        }
    }

    private static MistForwarder myApp; // myApp can not be used in thread mode
    private Thread threadMain;
    private boolean shutdownNow = false;

    private ForwardTarget sourceTarget;
    private HashMap<String, MistSinkWorker> workerPool = new HashMap<String, MistSinkWorker>();
    private int sourceSessId;
    private long messageCount = 0;
    private String status;
    private Date lastUpdate;
    private int packetSent;
    private EnabledLock enabledLock = new EnabledLock();

    private ThreadInvoker.OutputListener outputListener = new ThreadInvoker.OutputListener() {
        public void receiveOutput(String name, String msg) {
            myOut.println(String.format("%s: %s", name, msg));
        }
    };

    private ThreadInvoker.OutputListener errorListener = new ThreadInvoker.OutputListener() {
        public void receiveOutput(String name, String msg) {
            myErr.println(String.format("%s: %s", name, msg));
        }
    };

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                myApp.shutdown();
            }
        });
    }

    private void shutdown() {
        shutdownNow = true;
        disable();
        if(threadMain != null) {
            detachSource(sourceSessId);
            destroySession(sourceSessId);
            myOut.println(String.format("forward %d message(s)", messageCount));
        }
    }

    private void printUsage() {
        myOut.printf("Usage:%n");
        myOut.printf("      mist-forwarder [options [arguments...] ]... %n%n");
        myOut.printf("Options: %n");
        myOut.printf("  --from=TARGET, -f TARGET %n");
        myOut.printf("  --to=TARGET, -t TARGET %n");
        myOut.printf("        specify the source and destination target of forwarder %n");
        myOut.printf("        TARGET         = {BROKER_CONFIG,EXCHANGE|EXCHANGE} %n");
        myOut.printf("        BROKER_CONFIG  = {activemq|openmq},host:port[;host:port],user:password %n");
        myOut.printf("        EXCHANGE       = {queue|topic}:EXCHANGENAME %n%n");
        myOut.printf("  --help, -h %n");
        myOut.printf("        display help messages %n%n");
    }

    private int createSession(ForwardTarget target) throws MistException {
        int sess_id = -1;
        try {
            MistSession sess = new MistSession();
            sess.setErrorListener(errorListener);
            BufferedReader in = new BufferedReader(new InputStreamReader(sess.getInputStream()));
            if(!target.determined)
                sess.invoke("");
            else
                sess.invoke(String.format("-c %s -a %s -b %s", target.broker.getAddressString(), target.auth.toString(), target.broker.getType()));
            sess_id = Integer.parseInt(in.readLine().trim());
            sess.waitForComplete();
            in.close();
            sess.getInputStream().close();
        }
        catch(Exception e) {
            myErr.println(e.getMessage());
            exitCode = RetVal.CREATE_SESSION_FAILED.ordinal();
            throw new MistException(e.getMessage() + ". Can't create a session");
        }
        return sess_id;
    }

    private void detachSource(int sess_id) {
        try {
            MistSource source = new MistSource();
            source.setOutputListener(outputListener);
            source.setErrorListener(errorListener);
            source.invoke(String.format("%d --detach", sess_id));
            source.getThread().join();
        }
        catch(Exception e) {
            myErr.println(e.getMessage());
            exitCode = RetVal.DETACH_FAILED.ordinal();
        }
    }

    private void destroySession(int sess_id) {
        try {
            MistSession sess = new MistSession();
            sess.setOutputListener(outputListener);
            sess.setErrorListener(errorListener);
            sess.invoke(String.format("--destroy %d", sess_id));
            sess.getThread().join();
        }
        catch(Exception e) {
            myErr.println(e.getMessage());
            exitCode = RetVal.DESTROY_SESSION_FAILED.ordinal();
        }
    }

    private void addMistSinkWorker(ForwardTarget ft) throws MistException {
        MistSinkWorker ftWorker = new MistSinkWorker(ft);
        ftWorker.start();
        synchronized(workerPool) {
            workerPool.put(ft.exchange, ftWorker);
        }
    }

    private void removeMistSinkWorker(String target) {
        synchronized(workerPool) {
            if(workerPool.containsKey(target)) {
                MistSinkWorker worker = workerPool.remove(target);
                worker.close();
            }
        }
    }

    private void cleanupMistSinkWorker() {
        synchronized(workerPool) {
            for(Map.Entry<String, MistSinkWorker> ent : workerPool.entrySet()) {
                ent.getValue().close();
            }
            workerPool.clear();
        }
    }

    private void dispatchMessage(Packet pack) {
        synchronized(workerPool) {
            boolean blockingMode = (workerPool.size() == 1);
            for(Map.Entry<String, MistSinkWorker> ent : workerPool.entrySet()) {
                try {
                    MistMessage.MessageBlock.Builder mblock_builder = MistMessage.MessageBlock.newBuilder();
                    mblock_builder.mergeFrom(pack.getPayload());
                    mblock_builder.setId(ent.getKey());
                    ent.getValue().insertMessage(mblock_builder.build(), blockingMode);
                }
                catch(InvalidProtocolBufferException e) {
                    myErr.println(e.getMessage());
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public MistForwarder() {
        super("mist-forwarder");
        if(!Daemon.isRunning()) {
            myErr.println("Daemon not running");
            System.exit(-1);
        }
        exitCode = RetVal.OK.ordinal();
    }

    public void enable() {
        synchronized(enabledLock) {
            enabledLock.enabled = true;
            enabledLock.notify();
        }
    }

    public void disable() {
        synchronized(enabledLock) {
            enabledLock.enabled = false;
            enabledLock.notify();
        }
    }

    public boolean isEnabled() {
        return enabledLock.enabled;
    }
    
    public long getMessageCnt(String target) {
    	if(workerPool.containsKey(target)) {
            return workerPool.get(target).getDeliveryCount();
        }
        return -1;
    }

    public long getDropMessageCnt(String target) {
        if(workerPool.containsKey(target)) {
            return workerPool.get(target).getDropCount();
        }
        return -1;
    }

    public boolean mountDestination(String target) throws MistException {
        try {
            if(workerPool.containsKey(target))
                return false;

            addMistSinkWorker(new ForwardTarget(target));

            return true;
        }
        catch(MistException e) {
            throw e;
        }
    }

    public boolean unmountDestination(String target) {
        if(workerPool.containsKey(target)) {
            removeMistSinkWorker(target);

            return true;
        }
        return false;
    }

    public int execute(String argv[]) {
        messageCount = 0;

        LongOpt[] longopts = new LongOpt[] {
            new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'),
            new LongOpt("from", LongOpt.REQUIRED_ARGUMENT, null, 'f'),
            new LongOpt("to", LongOpt.REQUIRED_ARGUMENT, null, 't'),
        };

        boolean help = false;

        Getopt g = new Getopt("mist-forwarder", argv, "hf:t:", longopts);
        int c;
        String arg = null;
        try {
            while((c = g.getopt()) != -1) {
                switch(c) {
                case 'f':
                    arg = g.getOptarg();
                    sourceTarget = new ForwardTarget(arg);
                    break;
                case 't':
                    mountDestination(g.getOptarg());
                    break;
                case 'h':
                    help = true;
                    break;
                }
            }
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
            exitCode = RetVal.PARSE_ARGUMENT_FAILED.ordinal();
            return exitCode;
        }

        if(help)
            printUsage();
        else {
            if(sourceTarget == null || workerPool.size() == 0) {
                printUsage();
                exitCode = RetVal.PARSE_ARGUMENT_FAILED.ordinal();
                return exitCode;
            }

            // Create source
            MistSource mistSrc = null;
            BufferedInputStream in = null;
            Packet pack = new Packet();
            
            try {
                sourceSessId = createSession(sourceTarget);
                mistSrc = new MistSource();
                mistSrc.setOutputListener(outputListener);
                mistSrc.setErrorListener(errorListener);
                mistSrc.invoke(String.format("%d --mount %s", sourceSessId, sourceTarget.exchange));
                mistSrc.waitForComplete();
                in = new BufferedInputStream(mistSrc.getInputStream());
                mistSrc.invoke(sourceSessId + " --attach");            	
            } catch (Exception e) {
            	myErr.println(e.getMessage());
            	try {
            		if(in != null) in.close();
            		if(mistSrc != null) {
                        mistSrc.getInputStream().close();
                        mistSrc.getThread().join();
                        cleanupMistSinkWorker();
            		}
                }
                catch(Exception ex) {
                    myErr.println(ex.getMessage());
                }
                return exitCode;
            }                      

            doLoop:
            do {
                try {
                    if(pack.read(in) > 0) {
                        synchronized(enabledLock) {
                            while(!enabledLock.enabled) {
                                if(shutdownNow)
                                    break doLoop;
                                try {
                                    enabledLock.wait();
                                }
                                catch(InterruptedException e) {
                                }
                            }
                        }
                        dispatchMessage(pack);
                        messageCount++;
                    }
                    else {
                        exitCode = RetVal.INVALID_SESSION.ordinal();
                        break doLoop;
                    }
                }
                catch(IOException e) {
                    myErr.println(e.getMessage());
                }
            } while(!shutdownNow);

            try {
                in.close();
                mistSrc.getInputStream().close();
                mistSrc.getThread().join();
                cleanupMistSinkWorker();
            }
            catch(Exception e) {
                myErr.println(e.getMessage());
            }
        }
        return exitCode;
    }

    public void manualShutdown() {
        shutdown();
    }

    public int run(String argv[]) {
        threadMain = Thread.currentThread();
        execute(argv);
        threadMain = null;
        return exitCode;
    }

    public String getStatus() {
        return this.status;
    }

    public Date getLastUpdate() {
        return this.lastUpdate;
    }

    public int getPacketSent() {
        return this.packetSent;
    }

    public void setPacketSent(int sent) {
        this.packetSent = sent;
    }

    public int getSourceSessionID() {
        return sourceSessId;
    }

    public int [] getDestinationSessionID() {
        int [] id = new int[workerPool.size()];
        int i = 0;
        for(MistSinkWorker w: workerPool.values())
            id[i++] = w.getSessionID();
        return id;
    }

    public static void main(String argv[]) {
        myApp = new MistForwarder();
        myApp.threadMain = Thread.currentThread();
        myApp.addShutdownHook();
        myApp.execute(argv);
        myApp.threadMain = null;
        System.exit(myApp.exitCode);
    }
}
