package com.trendmicro.mist.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.proto.MistMessage;
import com.trendmicro.mist.util.OpenMQTestBroker;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.mist.util.ZKTestServer;
import com.trendmicro.spn.common.util.Utils;
import com.trendmicro.tme.mfr.BrokerFarm;

public class TestConsumerSession extends TestCase {
    private ZKTestServer zkTestServer;
    private BrokerFarm brokerFarm = new BrokerFarm();

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

    public void testOnMessage() throws MistException, UnknownHostException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException, JMSException, CODIException, JMSServiceException {
        /**
         * Setup open mq
         */
        OpenMQTestBroker brk = new OpenMQTestBroker("test", 9876);
        brk.start();
        brk.registerOnZk();
        for(int i = 0; i < 10; i++) {
            if(brokerFarm.getBrokerCount() == 1)
                break;
            Utils.justSleep(500);
        }
        assertEquals(1, brokerFarm.getBrokerCount());
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
            if(brokerFarm.getBrokerCount() == 1)
                break;
            Utils.justSleep(500);
        }
        assertEquals(1, brokerFarm.getBrokerCount());
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

        brk.stop();
    }

    public static Test suite() {
        return new TestSuite(TestConsumerSession.class);
    }
}
