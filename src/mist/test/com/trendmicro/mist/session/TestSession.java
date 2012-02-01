package com.trendmicro.mist.session;

import java.io.IOException;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageProducer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.mist.Client;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.OpenMQTestBroker;
import com.trendmicro.mist.util.ZKTestServer;
import com.trendmicro.spn.common.util.Utils;
import com.trendmicro.tme.mfr.BrokerFarm;

public class TestSession extends TestCase {
    private ZKTestServer zkTestServer;
    private BrokerFarm brokerFarm = new BrokerFarm();

    private GateTalk.Session genSessionConfig(String brokerType, String host, String port, String username, String password) {
        GateTalk.Connection.Builder connBuilder = GateTalk.Connection.newBuilder();
        connBuilder.setBrokerType(brokerType);
        connBuilder.setHostName(host);
        connBuilder.setHostPort(port);
        connBuilder.setUsername(username);
        connBuilder.setPassword(password);
        return GateTalk.Session.newBuilder().setConnection(connBuilder.build()).build();
    }

    private GateTalk.Client genClientConfig(String exName, boolean isSink, boolean isQueue) {
        GateTalk.Client.Builder builder = GateTalk.Client.newBuilder();
        builder.setChannel(GateTalk.Channel.newBuilder().setName(exName).setPersistent(false).setType(isQueue ? GateTalk.Channel.Type.QUEUE: GateTalk.Channel.Type.TOPIC).build());
        builder.setAction(GateTalk.Client.Action.MOUNT);
        builder.setSessionId(0);
        builder.setType(isSink ? GateTalk.Client.Type.PRODUCER: GateTalk.Client.Type.CONSUMER);

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

    public void testGetUniqueSessionId() {
        /**
         * Test normal incrementing
         */
        int sessId = UniqueSessionId.getInstance().getNewSessionId();
        assertTrue(sessId > 0);
        int newSessId = UniqueSessionId.getInstance().getNewSessionId();
        assertEquals(sessId + 1, newSessId);

        /**
         * Test boundary
         */
        UniqueSessionId generator = new UniqueSessionId(Integer.MAX_VALUE - 1);
        assertEquals(Integer.MAX_VALUE - 1, generator.getNewSessionId());
        assertEquals(Integer.MAX_VALUE, generator.getNewSessionId());
        assertEquals(1, generator.getNewSessionId());
    }

    public void testAddClient() throws MistException, ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException, IOException, JMSException, CODIException {
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

        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        Session sess = new ProducerSession(0, sessConfig);

        /**
         * Test add a normal queue client
         */
        GateTalk.Client clientConfig = genClientConfig("foo.out", true, true);
        sess.addClient(clientConfig);
        assertEquals(clientConfig, sess.findClient(new Exchange("queue:foo.out")).getConfig());

        /**
         * Test re-mount the same exchange
         */
        Exception ex = null;
        try {
            sess.addClient(clientConfig);
        }
        catch(MistException e) {
            ex = e;
        }
        assertEquals(MistException.ALREADY_MOUNTED, ex.getMessage());

        /**
         * Test add a normal topic client
         */
        clientConfig = genClientConfig("foo.out", true, false);
        sess.addClient(clientConfig);
        assertEquals(clientConfig, sess.findClient(new Exchange("topic:foo.out")).getConfig());

        /**
         * Test using mist-source to mount a producer session
         */
        clientConfig = genClientConfig("bar.out", false, true);
        ex = null;
        try {
            sess.addClient(clientConfig);
        }
        catch(MistException e) {
            ex = e;
        }
        assertEquals(MistException.INCOMPATIBLE_TYPE_SINK, ex.getMessage());

        brk.stop();
    }

    public void testOpen() throws MistException, JMSException, CODIException, ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException, IOException, JMSServiceException {
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

        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        Session sess = new ProducerSession(0, sessConfig);

        /**
         * Test add normal a queue client and open
         */
        GateTalk.Client clientConfig = genClientConfig("foo.out", true, true);
        Client c = sess.addClient(clientConfig);
        assertEquals(clientConfig, sess.findClient(new Exchange("queue:foo.out")).getConfig());
        sess.open(false);
        MessageProducer producer = sess.findClient(new Exchange("queue:foo.out")).getProducer();
        assertNotNull(producer);

        /**
         * Deliver a message
         */
        BytesMessage msg = c.getJMSSession().createBytesMessage();
        msg.writeBytes("test".getBytes());
        producer.send(msg);
        assertEquals("test", new String(brk.getMessage(true, "foo.out")));

        /**
         * Test add another queue client
         */
        clientConfig = genClientConfig("bar.out", true, true);
        c = sess.addClient(clientConfig);
        assertEquals(clientConfig, sess.findClient(new Exchange("queue:bar.out")).getConfig());
        sess.open(false);
        producer = sess.findClient(new Exchange("queue:bar.out")).getProducer();
        assertNotNull(producer);

        msg = c.getJMSSession().createBytesMessage();
        msg.writeBytes("test_bar".getBytes());
        producer.send(msg);
        assertEquals("test_bar", new String(brk.getMessage(true, "bar.out")));

        brk.stop();
    }

    public void testAttach() throws Exception {
        GateTalk.Session sessConfig = genSessionConfig("", "", "", "", "");
        Session producerSession = new ProducerSession(0, sessConfig);
        Session consumerSession = new ConsumerSession(0, sessConfig);
        /**
         * Test incompatible type
         */
        Exception ex = null;
        try {
            producerSession.attach(GateTalk.Request.Role.SOURCE);
        }
        catch(MistException e) {
            ex = e;
        }
        assertEquals(MistException.INCOMPATIBLE_TYPE_SINK, ex.getMessage());

        try {
            consumerSession.attach(GateTalk.Request.Role.SINK);
        }
        catch(MistException e) {
            ex = e;
        }
        assertEquals(MistException.INCOMPATIBLE_TYPE_SOURCE, ex.getMessage());

        /**
         * Test normal attach, the first connect will be successful, and the
         * following will be failed
         */
        producerSession.attach(GateTalk.Request.Role.SINK);
        assertTrue(Utils.checkSocketConnectable("localhost", producerSession.getCommPort()));
        for(int i = 0; i < 10; i++) {
            if(producerSession.isReady())
                break;
            Utils.justSleep(500);
        }
        assertTrue(producerSession.isReady());
        assertFalse(Utils.checkSocketConnectable("localhost", producerSession.getCommPort()));

        /**
         * Test attach when attached
         */
        ex = null;
        try {
            producerSession.attach(GateTalk.Request.Role.SINK);
        }
        catch(MistException e) {
            ex = e;
        }
        assertEquals(ex.getMessage(), MistException.ALREADY_ATTACHED);
    }

    public static Test suite() {
        return new TestSuite(TestSession.class);
    }
}
