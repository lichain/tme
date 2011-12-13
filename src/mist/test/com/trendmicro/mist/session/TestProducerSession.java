package com.trendmicro.mist.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import javax.jms.JMSException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.google.protobuf.ByteString;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.mfr.BrokerFarm;
import com.trendmicro.mist.mfr.RouteFarm;
import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.proto.MistMessage;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.OpenMQTestBroker;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.mist.util.ZKTestServer;
import com.trendmicro.spn.common.util.Utils;
import com.trendmicro.spn.proto.SpnMessage;
import com.trendmicro.spn.proto.SpnMessage.Container;
import com.trendmicro.spn.proto.SpnMessage.ContainerBase;
import com.trendmicro.spn.proto.SpnMessage.MessageBase;
import com.trendmicro.spn.proto.SpnMessage.MessageList;

public class TestProducerSession extends TestCase {
    private ZKTestServer zkTestServer;

    private GateTalk.Session genSessionConfig(String brokerType, String host, String port, String username, String password) {
        GateTalk.Connection.Builder connBuilder = GateTalk.Connection.newBuilder();
        connBuilder.setBrokerType(brokerType);
        connBuilder.setHostName(host);
        connBuilder.setHostPort(port);
        connBuilder.setUsername(username);
        connBuilder.setPassword(password);
        return GateTalk.Session.newBuilder().setConnection(connBuilder.build()).build();
    }

    private GateTalk.Client genClientConfig(String exName) {
        GateTalk.Client.Builder builder = GateTalk.Client.newBuilder();
        builder.setChannel(GateTalk.Channel.newBuilder().setName(exName).setPersistent(false).setType(GateTalk.Channel.Type.QUEUE).build());
        builder.setAction(GateTalk.Client.Action.MOUNT);
        builder.setSessionId(0);
        builder.setType(GateTalk.Client.Type.PRODUCER);

        return builder.build();
    }

    private Container genSPNMessage(byte[] msg) {
        MessageBase.Builder mbase_builder = MessageBase.newBuilder();
        mbase_builder.setSubject(ByteString.copyFrom("".getBytes()));

        SpnMessage.Message.Builder msg_builder = SpnMessage.Message.newBuilder();
        msg_builder.setMsgBase(mbase_builder.build());
        msg_builder.setDerived(ByteString.copyFrom(msg));

        MessageList.Builder mlist_builder = MessageList.newBuilder();
        mlist_builder.addMessages(msg_builder.build());

        ContainerBase.Builder cbase_builder = ContainerBase.newBuilder();
        cbase_builder.setMessageList(mlist_builder.build());

        Container.Builder cont_builder = Container.newBuilder();
        cont_builder.setContainerBase(cbase_builder.build());
        return cont_builder.build();
    }

    @Override
    protected void setUp() throws Exception {
        zkTestServer = new ZKTestServer(39979);
        zkTestServer.start();
        ZKSessionManager.initialize("localhost:39979", 8000);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        ZKSessionManager.uninitialize();
        zkTestServer.stop();

        super.tearDown();
    }

    @SuppressWarnings("unchecked")
    public void testUpdateRoute() throws SecurityException, NoSuchMethodException, MistException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, InterruptedException, ClassNotFoundException, InstantiationException, IOException, JMSException, CODIException {
        /**
         * Setup open mq
         */
        OpenMQTestBroker brk = new OpenMQTestBroker("test", 9876);
        brk.start();
        brk.registerOnZk();
        for(int i = 0; i < 10; i++) {
            if(BrokerFarm.getInstance().getBrokerCount() == 1)
                break;
            Utils.justSleep(500);
        }
        assertEquals(1, BrokerFarm.getInstance().getBrokerCount());
        assertTrue(Utils.checkSocketConnectable("localhost", 9876));
        
        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        GateTalk.Client clientConfig = genClientConfig("foo.out");

        ProducerSession sess = new ProducerSession(0, sessConfig);
        Method updateRoute = ProducerSession.class.getDeclaredMethod("updateRoute", new Class[] {});
        updateRoute.setAccessible(true);
        Field routeCacheMapField = ProducerSession.class.getDeclaredField("routeCacheMap");
        routeCacheMapField.setAccessible(true);
        HashMap<String, List<Exchange>> routeCacheMap = (HashMap<String, List<Exchange>>) (routeCacheMapField.get(sess));
        Field ttlField = ProducerSession.class.getDeclaredField("ROUTE_CACHE_TTL_MILLIS");
        ttlField.setAccessible(true);
        long cacheTTL = ttlField.getLong(sess);

        /**
         * The route cache should be empty at the beginning
         */
        assertTrue(routeCacheMap.isEmpty());

        Exchange fooOutEx = new Exchange("queue:foo.out");
        sess.addClient(clientConfig);
        Vector<Exchange> destList = new Vector<Exchange>();
        destList.add(new Exchange("bar.in"));
        RouteFarm.getInstance().getRouteTable().put(fooOutEx.getName(), destList);
        updateRoute.invoke(sess, new Object[] {});
        assertEquals("queue:bar.in", routeCacheMap.get(fooOutEx).get(0).toString());

        /**
         * Update the routing table and invoke updateRoute before the cache
         * expires, and the cache should remain the same
         */
        destList.add(new Exchange("log.in"));
        updateRoute.invoke(sess, new Object[] {});
        assertEquals("queue:bar.in", routeCacheMap.get(fooOutEx).get(0).toString());
        assertEquals(1, routeCacheMap.get(fooOutEx).size());

        /**
         * Wait after the cache expires and invoke updateRoute again
         */
        Thread.sleep(cacheTTL);
        updateRoute.invoke(sess, new Object[] {});
        assertEquals("queue:bar.in", routeCacheMap.get(fooOutEx).get(0).toString());
        assertEquals("queue:log.in", routeCacheMap.get(fooOutEx).get(1).toString());

        /**
         * Clear the routing table
         */
        RouteFarm.getInstance().getRouteTable().clear();
        Thread.sleep(cacheTTL);
        updateRoute.invoke(sess, new Object[] {});
        assertTrue(routeCacheMap.isEmpty());
        
        brk.stop();
    }

    public void testAckClient() throws MistException, SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, UnknownHostException, IOException, InterruptedException {
        /**
         * Setup session, socket and get the ackClient method
         */
        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        ProducerSession sess = new ProducerSession(0, sessConfig);
        sess.attach(GateTalk.Request.Role.SINK);
        Socket socket = new Socket("localhost", sess.getCommPort());
        assertTrue(socket.isConnected());
        for(int i = 0; i < 10; i++) {
            if(sess.isReady())
                break;
            Utils.justSleep(500);
        }
        assertTrue(sess.isReady());

        Method ackClient = ProducerSession.class.getDeclaredMethod("ackClient", new Class[] {
            com.trendmicro.mist.util.Packet.class, boolean.class, String.class
        });
        ackClient.setAccessible(true);

        Packet packet = new Packet();
        BufferedInputStream socketInput = new BufferedInputStream(socket.getInputStream());

        /**
         * Test success packet
         */
        ackClient.invoke(sess, new Object[] {
            packet, true, ""
        });
        packet.read(socketInput);
        assertEquals(GateTalk.Response.newBuilder().setSuccess(true).build().toByteString(), ByteString.copyFrom(packet.getPayload()));

        /**
         * Test fail packet
         */
        ackClient.invoke(sess, new Object[] {
            packet, false, "exception"
        });
        packet.read(socketInput);
        assertEquals(GateTalk.Response.newBuilder().setSuccess(false).setException("exception").build().toByteString(), ByteString.copyFrom(packet.getPayload()));

        socket.close();
    }

    public void testSendMessageFail() throws MistException, UnknownHostException, IOException, InterruptedException, CODIException {
        /**
         * Setup session, socket
         */
        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        ProducerSession sess = new ProducerSession(0, sessConfig);
        sess.attach(GateTalk.Request.Role.SINK);
        Socket socket = new Socket("localhost", sess.getCommPort());
        for(int i = 0; i < 10; i++) {
            if(sess.isReady())
                break;
            Utils.justSleep(500);
        }
        assertTrue(sess.isReady());

        Packet packet = new Packet();
        BufferedInputStream socketInput = new BufferedInputStream(socket.getInputStream());
        BufferedOutputStream socketOutput = new BufferedOutputStream(socket.getOutputStream());

        /**
         * Test message too large (over 20M)
         */
        byte[] largeMsg = new byte[Daemon.MAX_MESSAGE_SIZE + 1];
        new DataOutputStream(socketOutput).writeInt(Daemon.MAX_MESSAGE_SIZE + 1);
        socketOutput.write(largeMsg);
        packet.read(socketInput);
        GateTalk.Response res = GateTalk.Response.newBuilder().mergeFrom(packet.getPayload()).build();
        assertFalse(res.getSuccess());
        assertEquals(MistException.INVALID_MESSAGE_SIZE, res.getException());
    }

    public void testSendMessageOK() throws ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException, IOException, MistException, JMSServiceException, JMSException, CODIException {
        /**
         * Setup open mq
         */
        OpenMQTestBroker brk = new OpenMQTestBroker("test", 9876);
        brk.start();
        brk.registerOnZk();
        for(int i = 0; i < 10; i++) {
            if(BrokerFarm.getInstance().getBrokerCount() == 1)
                break;
            Utils.justSleep(500);
        }
        assertEquals(1, BrokerFarm.getInstance().getBrokerCount());
        assertTrue(Utils.checkSocketConnectable("localhost", 9876));

        /**
         * Setup session, socket
         */
        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        ProducerSession sess = new ProducerSession(0, sessConfig);
        GateTalk.Client clientConfig = genClientConfig("foo.out");
        sess.addClient(clientConfig);
        sess.attach(GateTalk.Request.Role.SINK);
        Socket socket = new Socket("localhost", sess.getCommPort());
        for(int i = 0; i < 10; i++) {
            if(sess.isReady())
                break;
            Utils.justSleep(500);
        }
        assertTrue(sess.isReady());

        Packet packet = new Packet();
        BufferedInputStream socketInput = new BufferedInputStream(socket.getInputStream());
        BufferedOutputStream socketOutput = new BufferedOutputStream(socket.getOutputStream());

        /**
         * Send a message to foo.out
         */
        MistMessage.MessageBlock msg = MistMessage.MessageBlock.newBuilder().setId("queue:foo.out").setMessage(ByteString.copyFrom("test".getBytes())).build();
        packet.setPayload(msg.toByteArray());
        packet.write(socketOutput);
        packet.read(socketInput);
        assertTrue(GateTalk.Response.newBuilder().mergeFrom(packet.getPayload()).build().getSuccess());
        byte[] recvMsg = brk.getMessage(true, "foo.out");
        assertEquals("test", new String(recvMsg));

        /**
         * Send a message to bar.out
         */
        msg = MistMessage.MessageBlock.newBuilder().setId("queue:bar.out").setMessage(ByteString.copyFrom("test-bar".getBytes())).build();
        packet.setPayload(msg.toByteArray());
        packet.write(socketOutput);
        packet.read(socketInput);
        assertTrue(GateTalk.Response.newBuilder().mergeFrom(packet.getPayload()).build().getSuccess());
        recvMsg = brk.getMessage(true, "bar.out");
        assertEquals("test-bar", new String(recvMsg));

        /**
         * Test Local Forwarding: foo.out->bar.in, ,log.in
         */
        Vector<Exchange> destList = new Vector<Exchange>();
        destList.add(new Exchange("bar.in"));
        destList.add(new Exchange(""));
        destList.add(new Exchange("log.in"));
        RouteFarm.getInstance().getRouteTable().put("foo.out", destList);
        // Wait until local routing cache expires
        Utils.justSleep(2000);
        msg = MistMessage.MessageBlock.newBuilder().setId("queue:foo.out").setMessage(ByteString.copyFrom("test-route".getBytes())).build();
        packet.setPayload(msg.toByteArray());
        packet.write(socketOutput);
        packet.read(socketInput);
        assertTrue(GateTalk.Response.newBuilder().mergeFrom(packet.getPayload()).build().getSuccess());
        recvMsg = brk.getMessage(true, "bar.in");
        assertEquals("test-route", new String(recvMsg));
        recvMsg = brk.getMessage(true, "log.in");
        assertEquals("test-route", new String(recvMsg));

        msg = MistMessage.MessageBlock.newBuilder().setId("queue:foo.out").setMessage(genSPNMessage("test-tls".getBytes()).toByteString()).build();
        packet.setPayload(msg.toByteArray());
        packet.write(socketOutput);
        packet.read(socketInput);
        assertTrue(GateTalk.Response.newBuilder().mergeFrom(packet.getPayload()).build().getSuccess());
        recvMsg = brk.getMessage(true, "bar.in");
        assertNotNull(recvMsg);
        recvMsg = brk.getMessage(true, "log.in");
        assertNotNull(recvMsg);
        recvMsg = brk.getMessage(true, "tlsEx");
        assertNotNull(recvMsg);
        Container tlsMsg = Container.newBuilder().mergeFrom(recvMsg).build();
        assertEquals("pre", tlsMsg.getLogInfo().getPrefix());
        assertEquals("type", tlsMsg.getLogInfo().getType());
        assertEquals(0, tlsMsg.getLogInfo().getVersion());
        assertEquals("send", tlsMsg.getLogInfo().getEvent());
        assertEquals("test-tls", new String(tlsMsg.getContainerBase().getMessageList().getMessages(0).getDerived().toByteArray()));

        brk.stop();
    }

    public void testDetach() throws MistException, UnknownHostException, IOException {
        /**
         * Setup an attached session
         */
        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        Session producerSession = new ProducerSession(0, sessConfig);
        producerSession.attach(GateTalk.Request.Role.SINK);
        int port = producerSession.getCommPort();
        Socket sock = new Socket("localhost", port);
        assertTrue(sock.isConnected());

        /**
         * Test incompatible session type
         */
        Exception ex = null;
        try {
            producerSession.detach(GateTalk.Request.Role.SOURCE);
        }
        catch(MistException e) {
            ex = e;
        }
        assertEquals(MistException.INCOMPATIBLE_TYPE_SINK, ex.getMessage());

        /**
         * Test successful detach
         */
        producerSession.detach(GateTalk.Request.Role.SINK);
        assertFalse(producerSession.isAttached());
    }

    public static Test suite() {
        return new TestSuite(TestProducerSession.class);
    }
}
