package com.trendmicro.mist.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.trendmicro.mist.MistException;
import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.proto.MistMessage.MessageBlock;
import com.trendmicro.mist.util.Packet;

/**
 * <b>Example of a consumer to receive a message:</b><br>
 * <br>
 * &#47;&#47; New a MistClient consumer instance<br>
 * MistClient mistClient = new MistClient(MistClient.Role.CONSUMER, 1);<br>
 * <br>
 * &#47;&#47; Mount the client to an exchange<br>
 * mistClient.mount(true, "exchange.foo");<br>
 * <br>
 * &#47;&#47; Attach the client<br>
 * mistClient.attach();<br>
 * <br>
 * &#47;&#47; Get a message<br>
 * MessagePair p = mistClient.getMessage();<br>
 * <br>
 * &#47;&#47; Do some processing on p.message &#47;&#47;<br>
 * <br>
 * &#47;&#47; Acknowledge the message<br>
 * p.acker.ack();<br>
 * <br>
 * &#47;&#47; Close the client<br>
 * mistClient.close();<br>
 * <br>
 * 
 * <b>Example of a producer to send a message:</b><br>
 * <br>
 * &#47;&#47; New a MistClient producer instance<br>
 * MistClient mistClient = new MistClient(MistClient.Role.PRODUCER, 1);<br>
 * <br>
 * &#47;&#47; Mount the client to an exchange<br>
 * mistClient.mount(true, "exchange.foo");<br>
 * <br>
 * &#47;&#47; Attach the client<br>
 * mistClient.attach();<br>
 * <br>
 * &#47;&#47; Write a message<br>
 * mistClient.writeMessage(message); <br>
 * <br>
 * &#47;&#47; Close the client<br>
 * mistClient.close();<br>
 * <br>
 * 
 */
public class MistClient {
    private static final int MISTD_PORT = 9498;
    private boolean isConsumer;
    private boolean onClose = false;
    private BlockingQueue<MessagePair> localQueue;
    private ArrayList<Session> sessionList = new ArrayList<Session>();
    private Thread waitingThread = null;
    private boolean error = false;
    List<MessageBlock> failList = new ArrayList<MessageBlock>();
    private String errorStr = null;

    public enum Role {
        /**
         * Specify this MistClient is a consumer
         */
        CONSUMER,
        /**
         * Specify this MistClient is a producer
         */
        PRODUCER,
    }

    public class Acker {
        private boolean acked = false;

        private Acker() {
        }

        private boolean acked() {
            return acked;
        }

        private void reset() {
            acked = false;
        }

        /**
         * Client call this function to acknowledge the message paired with the
         * Acker
         */
        public synchronized void ack() {
            acked = true;
            this.notify();
        }
    }

    public class MessagePair {
        /**
         * The message body
         */
        public MessageBlock message;
        /**
         * The Acker to ack the message
         */
        public Acker acker;

        private MessagePair(MessageBlock message, Acker acker) {
            this.message = message;
            this.acker = acker;
        }
    }

    class Session extends Thread {
        private int sessid;
        private Socket dataChannel = null;
        private Thread dataThread = null;
        private Acker acker = new Acker();

        private GateTalk.Command sendRequest(GateTalk.Command cmd) throws Exception {
            Socket s = null;
            try {
                s = new Socket("127.0.0.1", MISTD_PORT);
                Packet pack = new Packet();
                pack.setPayload(cmd.toByteArray());
                pack.write(new BufferedOutputStream(s.getOutputStream()));
                pack.read(new BufferedInputStream(s.getInputStream()));
                return GateTalk.Command.parseFrom(pack.getPayload());
            }
            catch(Exception e) {
                throw e;
            }
            finally {
                if(s != null) {
                    try {
                        s.close();
                    }
                    catch(Exception e) {
                    }
                }
            }
        }

        public Session() throws MistException {
            GateTalk.Command cmd = GateTalk.Command.newBuilder().addSession(GateTalk.Session.newBuilder().setConnection(GateTalk.Connection.newBuilder().setHostName("").setHostPort("").setUsername("").setPassword("").setBrokerType("").build()).build()).build();
            try {
                GateTalk.Command res = sendRequest(cmd);
                if(res.getResponseCount() != 0) {
                    if(res.getResponse(0).getSuccess()) {
                        sessid = Integer.valueOf(res.getResponse(0).getContext());
                        return;
                    }
                }
            }
            catch(Exception e) {
            }
            throw new MistException("cannot create session");
        }

        public void mount(boolean isQueue, String exName) throws MistException {
            GateTalk.Command cmd = GateTalk.Command.newBuilder().addClient(GateTalk.Client.newBuilder().setSessionId(sessid).setChannel(GateTalk.Channel.newBuilder().setType(isQueue ? GateTalk.Channel.Type.QUEUE: GateTalk.Channel.Type.TOPIC).setName(exName).build()).setAction(GateTalk.Client.Action.MOUNT).setType(isConsumer ? GateTalk.Client.Type.CONSUMER: GateTalk.Client.Type.PRODUCER).build()).build();
            try {
                GateTalk.Command res = sendRequest(cmd);
                if(res.getResponseCount() != 0) {
                    if(res.getResponse(0).getSuccess())
                        return;
                    else {
                        if(res.getResponse(0).getException().compareTo("exchange already mounted") == 0)
                            return;
                    }
                }
            }
            catch(Exception e) {
            }
            throw new MistException(String.format("cannot mount %s:%s", (isQueue ? "queue": "topic"), exName));
        }

        public void umount(boolean isQueue, String exName) throws MistException {
            GateTalk.Command cmd = GateTalk.Command.newBuilder().addClient(GateTalk.Client.newBuilder().setSessionId(sessid).setChannel(GateTalk.Channel.newBuilder().setType(isQueue ? GateTalk.Channel.Type.QUEUE: GateTalk.Channel.Type.TOPIC).setName(exName).build()).setAction(GateTalk.Client.Action.UNMOUNT).setType(isConsumer ? GateTalk.Client.Type.CONSUMER: GateTalk.Client.Type.PRODUCER).build()).build();
            try {
                GateTalk.Command res = sendRequest(cmd);
                if(res.getResponseCount() != 0) {
                    if(res.getResponse(0).getSuccess())
                        return;
                    else {
                        if(res.getResponse(0).getException().endsWith(" not found"))
                            return;
                        else if(res.getResponse(0).getException().compareTo("empty session") == 0)
                            return;
                    }
                }
            }
            catch(Exception e) {
            }
            throw new MistException(String.format("cannot umount %s:%s", (isQueue ? "queue": "topic"), exName));
        }

        public int getSessId() {
            return sessid;
        }

        public void attach() throws MistException {
            GateTalk.Command cmd = GateTalk.Command.newBuilder().addRequest(GateTalk.Request.newBuilder().setType(GateTalk.Request.Type.CLIENT_ATTACH).setArgument(Integer.valueOf(sessid).toString()).setRole(isConsumer ? GateTalk.Request.Role.SOURCE: GateTalk.Request.Role.SINK).build()).build();
            try {
                GateTalk.Command res = sendRequest(cmd);
                if(res.getResponseCount() != 0) {
                    if(res.getResponse(0).getSuccess()) {
                        int port = Integer.valueOf(res.getResponse(0).getContext());
                        dataChannel = new Socket();
                        dataChannel.setReuseAddress(true);
                        dataChannel.setTcpNoDelay(true);
                        dataChannel.connect(new InetSocketAddress("127.0.0.1", port));
                        this.start();
                        return;
                    }
                }
            }
            catch(Exception e) {
            }
            throw new MistException("cannot attach session");
        }

        public void run() {
            dataThread = this;
            BufferedInputStream bis = null;
            BufferedOutputStream bos = null;
            try {
                bis = new BufferedInputStream(dataChannel.getInputStream());
                bos = new BufferedOutputStream(dataChannel.getOutputStream());
                Packet packet = new Packet();
                if(isConsumer) {
                    GateTalk.Response ack = GateTalk.Response.newBuilder().setSuccess(true).build();
                    for(;;) {
                        if(packet.read(bis) <= 0)
                            break;
                        localQueue.put(new MessagePair(MessageBlock.parseFrom(packet.getPayload()), acker));
                        for(;;) {
                            synchronized(acker) {
                                acker.wait(500);
                                if(acker.acked())
                                    break;
                                else if(onClose)
                                    return;
                            }
                        }
                        packet.setPayload(ack.toByteArray());
                        packet.write(bos);
                        synchronized(acker) {
                            acker.reset();
                            acker.notify();
                        }
                        if(onClose)
                            return;
                    }
                }
                else {
                    boolean last = false;
                    do {
                        if(onClose)
                            last = true;
                        synchronized(acker) {
                            MessagePair pair = localQueue.poll(500, TimeUnit.MILLISECONDS);
                            if(pair != null) {
                                failList.add(pair.message);
                                packet.setPayload(pair.message.toByteArray());
                                packet.write(bos);
                                packet.read(bis);
                                GateTalk.Response.Builder responseBuilder = GateTalk.Response.newBuilder().mergeFrom(packet.getPayload());
                                if(!responseBuilder.getSuccess()){
                                    error = true;
                                    errorStr = responseBuilder.getException();
                                    break;
                                }
                                else {
                                    failList.remove(pair.message);
                                    pair.acker.ack();
                                }
                            }
                        }
                    } while(!last);
                    if(!error)
                        return;
                }
            }
            catch(Exception e) {
                System.err.println("===== MistClient Error =====");
                e.printStackTrace();
                System.err.println("============================");
            }
            try {
                waitingThread.interrupt();
                error = true;
            }
            catch(Exception e) {
            }
        }

        public void forceDestroy() {
            try {
                GateTalk.Command cmd = GateTalk.Command.newBuilder().addRequest(GateTalk.Request.newBuilder().setType(GateTalk.Request.Type.SESSION_DESTROY).setArgument(Integer.valueOf(sessid).toString()).build()).build();
                sendRequest(cmd);
            }
            catch(Exception e) {
            }
        }

        public void close() {
            try {
                synchronized(acker) {
                    if(acker.acked())
                        acker.wait();
                }
                GateTalk.Command cmd = GateTalk.Command.newBuilder().addRequest(GateTalk.Request.newBuilder().setType(GateTalk.Request.Type.SESSION_DESTROY).setArgument(Integer.valueOf(sessid).toString()).build()).build();
                sendRequest(cmd);
            }
            catch(Exception e) {
            }
            if(dataThread != null) {
                try {
                    dataChannel.close();
                    dataThread.join();
                }
                catch(InterruptedException e) {
                }
                catch(IOException e) {
                }
            }
        }
    }

    /**
     * 
     * @param role
     *            role is either a MistClient.Role.CONSUMER or a
     *            MistClient.Role.PRODUCER
     * @param numSession
     *            How many sessions to send to / receive from same exchanges
     * @throws MistException
     *             Unable to create mist session
     */
    public MistClient(Role role, int numSession) throws MistException {
        if(role == Role.CONSUMER)
            isConsumer = true;
        else
            isConsumer = false;
        for(int i = 0; i < numSession; i++)
            sessionList.add(new Session());
        localQueue = new ArrayBlockingQueue<MessagePair>(numSession);
    }

    /**
     * Mount an exchange for the client
     * 
     * @param isQueue
     *            If the exchange is queue, it is set to true, otherwise it is
     *            topic
     * @param exName
     *            The name of the exchange
     * @throws MistException
     *             Mount fails
     */
    public void mount(boolean isQueue, String exName) throws MistException {
        for(Session s : sessionList)
            s.mount(isQueue, exName);
    }

    /**
     * Unmount an exchange for the client
     * 
     * @param isQueue
     *            If the exchange is queue, it is set to true, otherwise it is
     *            topic
     * @param exName
     *            The name of the exchange
     * @throws MistException
     *             Unmount fails
     */
    public void umount(boolean isQueue, String exName) throws MistException {
        for(Session s : sessionList)
            s.umount(isQueue, exName);
    }

    /**
     * Attach the client to MIST
     * 
     * @throws MistException
     *             Attach fails
     */
    public void attach() throws MistException {
        for(Session s : sessionList)
            s.attach();
    }

    /**
     * Get session IDs acquired by this client
     * 
     * @return Session IDs
     */
    public int[] getSessionIdList() {
        int[] idList = new int[sessionList.size()];
        for(int i = 0; i < sessionList.size(); i++)
            idList[i] = sessionList.get(i).getSessId();
        return idList;
    }

    /**
     * If there is any message to be consumed
     * 
     * @return true - There is at least one message ready to be consumed<br>
     *         false - There is no message to be consumed currently
     */
    public boolean hasMessage() {
        return(!localQueue.isEmpty());
    }

    /**
     * Get a message, but not to be blocked
     * 
     * @return If success, returns the MessagePair<br>
     *         If there is no message, returns null
     * @throws MistException
     *             If the client is closed.
     */
    public MessagePair getMessageNoWait() throws MistException {
        if(!hasMessage())
            return null;
        return getMessage(10);
    }

    /**
     * Get a message, will be blocked in the timeout period if there is no
     * message
     * 
     * @param timeout
     *            The timeout period in millisecond. If the value is 0, then
     *            getMessage will block forever.
     * @return If success, returns the MessagePair<br>
     *         If there is no message, returns null
     * @throws MistException
     *             If the client is closed.
     */
    public MessagePair getMessage(int timeout) throws MistException {
        if(error)
            throw new MistException("session error, close and recreate it");
        if(onClose)
            throw new MistException("session closed");
        waitingThread = Thread.currentThread();
        if(timeout > 0) {
            try {
                return localQueue.poll(timeout, TimeUnit.MILLISECONDS);
            }
            catch(InterruptedException e) {
                throw new MistException("connection to MIST is broken!");
            }
        }
        else {
            try {
                return localQueue.take();
            }
            catch(InterruptedException e) {
                throw new MistException("connection to MIST is broken!");
            }
        }
    }

    /**
     * Get a message, block forever if there is no message
     * 
     * @return The MessagePair
     * @throws MistException
     *             If the client is closed.
     */
    public MessagePair getMessage() throws MistException {
        return getMessage(0);
    }

    /**
     * Whether the write message operation will probably be blocked or not
     * 
     * @return true - The client has some buffer space to deliver a message<br>
     *         false - The client has no buffer space left, and the succeeding
     *         call to writeMessage might get blocked
     */
    public boolean canWriteMessage() {
        return(localQueue.remainingCapacity() > 0);
    }

    /**
     * Non-blocking function to deliver a message
     * 
     * @param msg
     *            The message to be delivered
     * @return true - If the message can be put into MistClient's buffer and be
     *         delivered later<br>
     *         false - If MistClient is trying to deliver previous messages
     * @throws MistException
     *             The session is closed
     */
    public boolean writeMessageNoWait(MessageBlock msg) throws MistException {
        if(!canWriteMessage())
            return false;
        return writeMessage(msg, 10);
    }

    /**
     * Blocking function to deliver a message
     * 
     * @param msg
     *            The message to be delivered
     * @throws MistException
     *             The session is closed
     */
    public void writeMessage(MessageBlock msg) throws MistException {
        writeMessage(msg, 0);
    }

    /**
     * The write operation will be blocked in the specific timeout period
     * 
     * @param msg
     *            The message to be delivered
     * @param timeout
     *            Timeout period (millisecond). If the value is 0, then it will
     *            block forever
     * @return true - If the message is delivered to the broker<br>
     *         false - If the timeout is reached
     * @throws MistException
     *             The session is closed or sending error occurs
     */
    public boolean writeMessage(MessageBlock msg, int timeout) throws MistException {
        if(error)
            throw new MistException("session error, close and recreate it");
        if(onClose)
            throw new MistException("session closed");
        waitingThread = Thread.currentThread();

        long startTs = System.currentTimeMillis();
        Acker acker = new Acker();
        if(timeout > 0) {
            try {
                if(!localQueue.offer(new MessagePair(msg, acker), timeout, TimeUnit.MILLISECONDS))
                    return false;

                long timeElapsed = (System.currentTimeMillis() - startTs);
                long timeLeft = timeout - timeElapsed;
                if(timeLeft <= 0)
                    return false;
                synchronized(acker) {
                    // if it is already acked, return true, or wait to be acked
                    if(acker.acked())
                        return true;
                    acker.wait(timeLeft);
                    return acker.acked();
                }
            }
            catch(InterruptedException e) {
                if(errorStr == null)
                    throw new MistException("connection to MIST is broken!");
                else
                    throw new MistException(errorStr);
            }
        }
        else {
            try {
                localQueue.put(new MessagePair(msg, acker));
                synchronized(acker) {
                    if(acker.acked())
                        return true;
                    acker.wait();
                    return acker.acked();
                }
            }
            catch(InterruptedException e) {
                if(errorStr == null)
                    throw new MistException("connection to MIST is broken!");
                else
                    throw new MistException(errorStr);
            }
        }
    }

    /**
     * Close the client, will be blocked until all messages are delivered to
     * MIST
     * 
     * @throws MistException
     *             The close operation encounters some error
     */
    public void close() throws MistException {
        close(0);
    }

    class Killer extends Thread {
        public void run() {
            try {
                Thread.sleep(20000);
                for(Session s : sessionList)
                    s.forceDestroy();
            }
            catch(InterruptedException e) {
            }
        }
    }

    /**
     * Close the client, will be blocked in the specific timeout period. If
     * there are messages fail to be delivered to MIST, they will be returned
     * 
     * @param timeoutSec
     *            The timeout period (second)
     * @return List of messages - If close timed out, return messages might fail
     *         to be delivered, if every messages are successfully delivered,
     *         then the list is empty
     * 
     * @throws MistException
     *             The close operation encounters some error
     */
    public List<MessageBlock> close(int timeoutSec) throws MistException {
        onClose = true;
        long invokeTime = new Date().getTime() / 1000;
        long timeoutLong = (long) timeoutSec;
        for(;;) {
            if(isConsumer)
                break;
            if(timeoutLong > 0 && (new Date().getTime() / 1000 - invokeTime) > timeoutLong) {
                for(MessagePair p : localQueue)
                    failList.add(p.message);
                break;
            }
            if(localQueue.isEmpty())
                break;
            else {
                try {
                    Thread.sleep(1000);
                }
                catch(Exception e) {
                }
            }
        }
        Killer killer = new Killer();
        killer.start();
        for(Session s : sessionList)
            s.close();
        killer.interrupt();
        try {
            killer.join();
        }
        catch(InterruptedException e) {
        }
        return failList;
    }

    /**
     * Return a flag to indicates if MistClient is invalid
     * 
     * @return true - if no error occurs<br>
     *         false - if any error occurs and MistClient is no longer valid
     */
    public boolean isError() {
        return error;
    }
}
