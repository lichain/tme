package com.trendmicro.mist.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;

import com.google.protobuf.ByteString;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.codi.ZNode;
import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.mfr.BrokerFarm;
import com.trendmicro.mist.mfr.ExchangeFarm;
import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.proto.MistMessage;
import com.trendmicro.mist.proto.MistMessage.KeyValuePair;
import com.trendmicro.mist.proto.MistMessage.MessageBlock;
import com.trendmicro.mist.proto.ZooKeeperInfo.TLSConfig;
import com.trendmicro.mist.session.ConsumerSession.MessagePrepared;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.GOCTestServer;
import com.trendmicro.mist.util.GOCUtils;
import com.trendmicro.mist.util.OpenMQTestBroker;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.mist.util.ZKTestServer;
import com.trendmicro.spn.common.util.Utils;
import com.trendmicro.spn.proto.SpnMessage;
import com.trendmicro.spn.proto.SpnMessage.Container;
import com.trendmicro.spn.proto.SpnMessage.ContainerBase;
import com.trendmicro.spn.proto.SpnMessage.MessageBase;
import com.trendmicro.spn.proto.SpnMessage.MessageList;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestConsumerSession extends TestCase {
    private ZKTestServer zkTestServer;

    class OnMessageRunner extends Thread {
        ConsumerSession sess;
        Message msg;

        public OnMessageRunner(ConsumerSession sess, Message msg) {
            this.sess = sess;
            this.msg = msg;
        }

        @Override
        public void run() {
            sess.onMessage(msg);
        }
    }

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
        builder.setType(GateTalk.Client.Type.CONSUMER);

        return builder.build();
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

    public void testMessagePrepared() throws ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException, IOException, CODIException, JMSException, JMSServiceException, MistException {
        ExchangeFarm.getInstance().reset();
        /**
         * Create a goc exchange node and wait until it takes effect
         */
        GOCTestServer gocServer = new GOCTestServer(12345);
        GOCUtils gocClient = new GOCUtils();

        ZNode gocConfigNode = new ZNode("/tme2/global/goc_server");
        gocConfigNode.create(false, "http://localhost:12345/depot/*".getBytes());

        ZNode gocNode = new ZNode("/tme2/global/goc_exchange/gocEx");
        gocNode.create(false, "gocEx".getBytes());
        for(int i = 0; i < 10; i++) {
            if(ExchangeFarm.getInstance().belongsGOC("gocEx"))
                break;
            Thread.sleep(500);
        }
        assertTrue(ExchangeFarm.getInstance().belongsGOC("gocEx"));

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

        brk.sendMessage(true, "foo.in", "test".getBytes());
        BytesMessage msgin = brk.getBytesMessage(true, "foo.in");
        assertNotNull(msgin);

        MessagePrepared mp = new MessagePrepared(msgin, null);
        assertEquals("test", new String(mp.msgBlock.getMessage().toByteArray()));

        byte[] raw = new byte[Daemon.MAX_TRANSMIT_MESSAGE_SIZE + 1];
        MistMessage.MessageBlock mBlock = MistMessage.MessageBlock.newBuilder().setId("gocEx").setMessage(genSPNMessage(raw).toByteString()).build();
        ProducerSession.MessagePrepared promp = new ProducerSession.MessagePrepared(mBlock.toByteArray(), gocClient);
        brk.sendMessage(promp.dest.isQueue(), promp.dest.getName(), promp.msg);

        /**
         * Test download from goc fail
         */
        msgin = brk.getBytesMessage(true, "gocEx");
        assertNotNull(msgin);
        gocServer.close();
        Exception ex = null;
        ConsumerSession.MessagePrepared conmp = null;
        try {
            conmp = new ConsumerSession.MessagePrepared(msgin, gocClient);
        }
        catch(MistException e) {
            ex = e;
        }
        assertTrue(ex.getMessage().startsWith("unable to download"));

        /**
         * Test download from goc success
         */
        gocServer = new GOCTestServer(12345);
        promp = new ProducerSession.MessagePrepared(mBlock.toByteArray(), gocClient);
        brk.sendMessage(promp.dest.isQueue(), promp.dest.getName(), promp.msg);
        msgin = brk.getBytesMessage(true, "gocEx");
        assertNotNull(msgin);
        conmp = new ConsumerSession.MessagePrepared(msgin, gocClient);
        assertEquals(mBlock.getMessage(), conmp.msgBlock.getMessage());

        /**
         * Test add properties in mist message
         */
        mBlock = MistMessage.MessageBlock.newBuilder().setId("test").setMessage(ByteString.copyFrom("test".getBytes())).setTtl(5566L).addProperties(KeyValuePair.newBuilder().setKey("key").setValue("value").build()).build();
        promp = new ProducerSession.MessagePrepared(mBlock.toByteArray(), gocClient);
        HashMap<String, String> props = new HashMap<String, String>();
        props.put("key", "value");
        brk.sendMessage(promp.dest.isQueue(), promp.dest.getName(), promp.msg, 5566L, props);
        msgin = brk.getBytesMessage(true, "test");
        assertNotNull(msgin);
        conmp = new ConsumerSession.MessagePrepared(msgin, gocClient);
        assertEquals(mBlock.getMessage(), conmp.msgBlock.getMessage());
        assertEquals(mBlock.getTtl(), conmp.msgBlock.getTtl());
        assertEquals(mBlock.getProperties(0), conmp.msgBlock.getProperties(0));
        assertNull(conmp.tlsExchange);
        assertNull(conmp.tlsMessage);

        /**
         * Test TLS message
         */
        TLSConfig tlsConfig = TLSConfig.newBuilder().setLogChannel("tlsEx").setPrefix("pre").setType("type").setVersion(0).build();
        ZNode tlsNode = new ZNode("/tme2/global/tls_exchange/queue:test");
        tlsNode.create(false, tlsConfig.toString());
        for(int i = 0; i < 10; i++) {
            if(ExchangeFarm.getInstance().belongsTLS("queue:test") != null)
                break;
            Utils.justSleep(500);
        }
        assertNotNull(ExchangeFarm.getInstance().belongsTLS("queue:test"));

        mBlock = MessageBlock.newBuilder().setId("test").setMessage(genSPNMessage("test".getBytes()).toByteString()).build();
        promp = new ProducerSession.MessagePrepared(mBlock.toByteArray(), gocClient);
        brk.sendMessage(promp.dest.isQueue(), promp.dest.getName(), promp.msg, 5566L, props);
        msgin = brk.getBytesMessage(true, "test");
        assertNotNull(msgin);
        conmp = new ConsumerSession.MessagePrepared(msgin, gocClient);

        Container tlsMessage = conmp.tlsMessage;
        assertEquals(new Exchange("tlsEx"), conmp.tlsExchange);
        assertNotNull(tlsMessage);
        assertEquals("recv", tlsMessage.getLogInfo().getEvent());
        assertEquals("type", tlsMessage.getLogInfo().getType());
        assertEquals("pre", tlsMessage.getLogInfo().getPrefix());
        assertEquals(0, tlsMessage.getLogInfo().getVersion());
        assertEquals("test", new String(tlsMessage.getContainerBase().getMessageList().getMessages(0).getDerived().toByteArray()));

        brk.stop();
        gocServer.close();
    }

    public void testOnMessage() throws MistException, UnknownHostException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException, JMSException, CODIException, JMSServiceException {
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
         * Session setup and attach
         */
        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        ConsumerSession sess = new ConsumerSession(0, sessConfig);
        sess.attach(GateTalk.Request.Role.SOURCE);
        Socket socket = new Socket("localhost", sess.getCommPort());
        assertTrue(socket.isConnected());
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
         * Get a message and ack
         */
        brk.sendMessage(true, "foo.in", "foo".getBytes());
        BytesMessage msgin = brk.getBytesMessage(true, "foo.in");
        new OnMessageRunner(sess, msgin).start();
        packet.read(socketInput);
        MistMessage.MessageBlock mBlock = MistMessage.MessageBlock.newBuilder().mergeFrom(packet.getPayload()).build();
        assertEquals("queue:foo.in", mBlock.getId());
        assertEquals("foo", new String(mBlock.getMessage().toByteArray()));

        packet.setPayload(GateTalk.Response.newBuilder().setSuccess(true).build().toByteArray());
        packet.write(socketOutput);

        brk.stop();
    }

    public void testReceiveMessage() throws ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException, IOException, CODIException, JMSException, MistException, JMSServiceException {
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
         * Session setup and attach and mount foo.in
         */
        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        ConsumerSession sess = new ConsumerSession(0, sessConfig);
        GateTalk.Client clientConfig = genClientConfig("foo.in");
        // sess.addClient(new Client(clientConfig, sessConfig));
        sess.addClient(clientConfig);
        sess.attach(GateTalk.Request.Role.SOURCE);
        Socket socket = new Socket("localhost", sess.getCommPort());
        assertTrue(socket.isConnected());
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
         * Send and receive a message from foo.in
         */
        brk.sendMessage(true, "foo.in", "foo".getBytes());
        packet.read(socketInput);
        MistMessage.MessageBlock mBlock = MistMessage.MessageBlock.newBuilder().mergeFrom(packet.getPayload()).build();
        assertEquals("queue:foo.in", mBlock.getId());
        assertEquals("foo", new String(mBlock.getMessage().toByteArray()));

        packet.setPayload(GateTalk.Response.newBuilder().setSuccess(true).build().toByteArray());
        packet.write(socketOutput);

        /**
         * Test dynamic mount bar.in
         */
        brk.sendMessage(true, "bar.in", "bar".getBytes());
        clientConfig = genClientConfig("bar.in");
        sess.addClient(clientConfig);

        packet.read(socketInput);
        mBlock = MistMessage.MessageBlock.newBuilder().mergeFrom(packet.getPayload()).build();
        assertEquals("queue:bar.in", mBlock.getId());
        assertEquals("bar", new String(mBlock.getMessage().toByteArray()));

        packet.setPayload(GateTalk.Response.newBuilder().setSuccess(true).build().toByteArray());
        packet.write(socketOutput);

        /**
         * Test TLS logging on
         */
        TLSConfig tlsConfig = TLSConfig.newBuilder().setLogChannel("tlsEx").setPrefix("pre").setType("type").setVersion(0).build();
        ZNode tlsNode = new ZNode("/tme2/global/tls_exchange/queue:test");
        tlsNode.create(false, tlsConfig.toString());
        for(int i = 0; i < 10; i++) {
            if(ExchangeFarm.getInstance().belongsTLS("queue:test") != null)
                break;
            Utils.justSleep(500);
        }
        assertNotNull(ExchangeFarm.getInstance().belongsTLS("queue:test"));
        TlsSender.reset();
        brk.sendMessage(true, "test", genSPNMessage("test".getBytes()).toByteArray());
        clientConfig = genClientConfig("test");
        sess.addClient(clientConfig);
        packet.read(socketInput);
        mBlock = MistMessage.MessageBlock.newBuilder().mergeFrom(packet.getPayload()).build();
        assertEquals("queue:test", mBlock.getId());

        Container tlsMsg = Container.newBuilder().mergeFrom(brk.getMessage(true, "tlsEx")).build();
        assertEquals("pre", tlsMsg.getLogInfo().getPrefix());
        assertEquals("type", tlsMsg.getLogInfo().getType());
        assertEquals(0, tlsMsg.getLogInfo().getVersion());
        assertEquals("recv", tlsMsg.getLogInfo().getEvent());
        assertEquals("test", new String(tlsMsg.getContainerBase().getMessageList().getMessages(0).getDerived().toByteArray()));

        packet.setPayload(GateTalk.Response.newBuilder().setSuccess(true).build().toByteArray());
        packet.write(socketOutput);

        brk.stop();
    }

    public static Test suite() {
        return new TestSuite(TestConsumerSession.class);
    }
}
