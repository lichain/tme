package com.trendmicro.mist.cmd;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.google.protobuf.InvalidProtocolBufferException;
import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.ThreadInvoker;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.mist.util.Exchange;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.proto.MistMessage;

public class MistSink extends ThreadInvoker {
    enum CmdType {
        NONE, LIST, MOUNT, UNMOUNT, ATTACH, DETACH,
    }
    
    enum RetVal {
        OK,
        MOUNT_FAILED,
        UNMOUNT_FAILED,
        ATTACH_FAILED,
        DETACH_FAILED,
    }

    private Exchange exchange = new Exchange();
    private int targetSessionId = -1;

    private CmdType currCmd = CmdType.NONE;
    private Thread threadMain;
    private static MistSink myApp;
    private boolean countMessage = false;
    private boolean shutdownNow = false;
    private boolean usePerf = false;
    private long msgCount = 0;
    private final int PERF_COUNT = 1000;
    
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                myApp.shutdown();
            }
        });
    }

    private void shutdown() {
        shutdownNow = true;
        try {
            if(threadMain != null)
                threadMain.join(1000);
        }
        catch(InterruptedException e) {
        }
    }

    private void printUsage() {
        myOut.printf("Usage:%n");
        myOut.printf("      mist-sink SESSION_ID [options [arguments...] ]... %n%n");
        myOut.printf("Options: %n");
        myOut.printf("  --mount=EXCHANGE, -m EXCHANGE %n");
        myOut.printf("  --unmount=EXCHANGE, -u EXCHANGE %n");
        myOut.printf("        mount/unmount exchange to/from SESSION_ID %n");
        myOut.printf("        where EXCHANGE={queue,topic}:EXCHANGENAME %n");
        myOut.printf("    --queue, -q %n");
        myOut.printf("        use queue (default) %n");
        myOut.printf("    --topic, -t %n");
        myOut.printf("        use topic %n%n");
        myOut.printf("  --attach, -a %n");
        myOut.printf("        attach to SESSION_ID and start transmission %n%n");
        myOut.printf("    --perf, -p %n");
        myOut.printf("        display performance number %n%n");
        myOut.printf("    --count, -c %n");
        myOut.printf("        count messages %n%n");
        myOut.printf("  --detach, -d %n");
        myOut.printf("        detach from SESSION_ID %n%n");
        myOut.printf("  --help, -h %n");
        myOut.printf("        display help messages %n%n");
    }

    private void processAttach() {
        GateTalk.Request.Builder req_builder = GateTalk.Request.newBuilder();
        req_builder.setType(GateTalk.Request.Type.CLIENT_ATTACH);
        req_builder.setArgument(String.valueOf(targetSessionId));
        req_builder.setRole(GateTalk.Request.Role.SINK);

        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addRequest(req_builder.build());
        int commPort;
        try {
            GateTalk.Response res = MistSession.sendRequest(cmd_builder.build());
            if(!res.getSuccess()) {
                myErr.printf("failed: %s %n", res.getException());
                exitCode = RetVal.ATTACH_FAILED.ordinal();
                return;
            }
            commPort = Integer.parseInt(res.getContext());
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
            exitCode = RetVal.ATTACH_FAILED.ordinal();
            return;
        }

        Socket sock = null;
        BufferedInputStream socketIn = null;
        BufferedOutputStream socketOut = null;
        try {
            sock = new Socket();
            sock.setReuseAddress(true);
            sock.setTcpNoDelay(true);
            sock.connect(new InetSocketAddress("127.0.0.1", commPort));
            BufferedInputStream stdin = new BufferedInputStream(myIn);
            socketIn = new BufferedInputStream(sock.getInputStream());
            socketOut = new BufferedOutputStream(sock.getOutputStream());
            Packet pack = new Packet();
            Packet ackPack = new Packet();
            int rdcnt = -1;
            msgCount = 0;
            long prev_time = System.nanoTime();
            do {
                if((rdcnt = pack.read(stdin)) > 0) {
                    pack.write(socketOut);

                    if(ackPack.read(socketIn)<=0)
                        break;
                    try{
                        if(!GateTalk.Response.parseFrom(ackPack.getPayload()).getSuccess()){
                            myErr.println(GateTalk.Response.parseFrom(ackPack.getPayload()).getException());
                            continue;
                        }                        
                    }
                    catch(Exception e){
                        myErr.println(e.getMessage());
                        continue;
                    }
                    msgCount++;

                    if(usePerf && msgCount % PERF_COUNT == 0) {
                        long curr_time = System.nanoTime();
                        float duration = (float) (curr_time - prev_time) / (1000000000);
                        myErr.printf("mist-sink: %.2f mps%n", (float) PERF_COUNT / duration);
                        prev_time = curr_time;
                    }
                    if(countMessage) {
                        try {
                            MistMessage.MessageBlock.Builder mblock_builder = MistMessage.MessageBlock.newBuilder();
                            mblock_builder.mergeFrom(pack.getPayload());
                            MistMessage.MessageBlock msg_block = mblock_builder.build();
                            myOut.printf("message %d: %d bytes %n", msgCount, msg_block.getMessage().size());
                        }
                        catch(InvalidProtocolBufferException e) {
                            myOut.printf("message %d: %d bytes %n", msgCount, pack.getPayload().length);
                        }
                    }
                }
                if(shutdownNow)
                    break;
            } while(rdcnt != -1);
            Packet.writeSize(socketOut, -1);
        }
        catch(IOException e) {
            myErr.println(e.getMessage());
        }
        finally {
            try {
                socketIn.close();
                socketOut.close();
                sock.close();
            }
            catch(IOException e) {
                myErr.println(e.getMessage());
            }
        }
    }

    private void processMount() {
        GateTalk.Client client = MistSession.makeClientRequest(targetSessionId, exchange, false, true);
        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addClient(client);
        try {
            GateTalk.Response res = MistSession.sendRequest(cmd_builder.build());
            if(res.getSuccess()) 
                myOut.printf("%s%n", res.getContext());
            else {
                myErr.printf("failed: %s%n", res.getException());
                exitCode = RetVal.MOUNT_FAILED.ordinal();
            }
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
        }
    }

    private void processUnmount() {
        GateTalk.Client client = MistSession.makeClientRequest(targetSessionId, exchange, false, false);
        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addClient(client);
        try {
            GateTalk.Response res = MistSession.sendRequest(cmd_builder.build());
            if(res.getSuccess()) 
                myOut.printf("%s%n", res.getContext());
            else {
                myErr.printf("failed: %s%n", res.getException());
                exitCode = RetVal.UNMOUNT_FAILED.ordinal();
            }
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
        }
    }

    private void processDetach() {
        GateTalk.Request.Builder req_builder = GateTalk.Request.newBuilder();
        req_builder.setType(GateTalk.Request.Type.CLIENT_DETACH);
        req_builder.setRole(GateTalk.Request.Role.SINK);
        req_builder.setArgument(String.valueOf(targetSessionId));
        
        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addRequest(req_builder.build());
        try {
            GateTalk.Response res = MistSession.sendRequest(cmd_builder.build());
            if(res.getSuccess()) 
                myOut.printf("%s%n", res.getContext());
            else {
                myErr.printf("failed: %s%n", res.getException());
                exitCode = RetVal.DETACH_FAILED.ordinal();
            }
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public MistSink() {
        super("mist-sink");
        if(!Daemon.isRunning()) { 
            System.err.println("Daemon not running");
            System.exit(-1);
        }
        exitCode = RetVal.OK.ordinal();
    }

    public int run(String argv[]) {
        exchange.reset();
        
        LongOpt[] longopts = new LongOpt[] {
            new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'), 
            new LongOpt("mount", LongOpt.REQUIRED_ARGUMENT, null, 'm'), 
            new LongOpt("unmount", LongOpt.REQUIRED_ARGUMENT, null, 'u'), 
            new LongOpt("attach", LongOpt.NO_ARGUMENT, null, 'a'), 
            new LongOpt("detach", LongOpt.NO_ARGUMENT, null, 'd'), 
            new LongOpt("topic", LongOpt.NO_ARGUMENT, null, 't'), 
            new LongOpt("queue", LongOpt.NO_ARGUMENT, null, 'q'), 
            new LongOpt("count", LongOpt.NO_ARGUMENT, null, 'c'), 
            new LongOpt("perf", LongOpt.NO_ARGUMENT, null, 'p'),
        };

        Getopt g = new Getopt("mist-sink", argv, "hm:u:adtqcp", longopts);
        int c;
        while((c = g.getopt()) != -1) {
            switch(c) {
            case 'm':
                currCmd = CmdType.MOUNT;
                exchange.set(g.getOptarg());
                break;
            case 'u':
                currCmd = CmdType.UNMOUNT;
                exchange.set(g.getOptarg());
                break;
            case 'a':
                currCmd = CmdType.ATTACH;
                break;
            case 'd':
                currCmd = CmdType.DETACH;
                break;
            case 'q':
                exchange.setQueue();
                break;
            case 't':
                exchange.setTopic();
                break;
            case 'c':
                countMessage = true;
                break;
            case 'p':
                usePerf = true;
                break;
            }
        }

        try {
            if(g.getOptind() < argv.length)
                targetSessionId = Integer.parseInt(argv[g.getOptind()]);
            else {
                myErr.printf("no SESSION_ID specified %n");
                currCmd = CmdType.NONE;
            }

            if(currCmd != CmdType.ATTACH) {
                if(countMessage)
                    myErr.printf("warning: invalid option `--count'%n");
                if(usePerf)
                    myErr.printf("warning: invalid option `--perf'%n");
            }

            if(currCmd == CmdType.MOUNT)
                processMount();
            else if(currCmd == CmdType.UNMOUNT)
                processUnmount();
            else if(currCmd == CmdType.ATTACH)
                processAttach();
            else if(currCmd == CmdType.DETACH)
                processDetach();
            else if(currCmd == CmdType.NONE)
                printUsage();
        }
        catch(NumberFormatException e) {
            myErr.printf("%s, invalid number format %n", e.getMessage());
        }
        catch(Exception e) {
            myErr.println(e.getMessage());
        }
        return exitCode;
    }
    
    public long getMessageCount() {
    	return msgCount;
    }

    public static void main(String argv[]) {
        myApp = new MistSink();
        myApp.threadMain = Thread.currentThread();
        myApp.addShutdownHook();
        myApp.run(argv);
        myApp.threadMain = null;
        System.exit(myApp.exitCode);
    }
}
