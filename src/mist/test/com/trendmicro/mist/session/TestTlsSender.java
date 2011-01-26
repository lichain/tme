package com.trendmicro.mist.session;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import javax.jms.JMSException;

import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.mfr.BrokerFarm;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.OpenMQTestBroker;
import com.trendmicro.mist.util.ZKTestServer;
import com.trendmicro.spn.common.util.Utils;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestTlsSender extends TestCase {
    private ZKTestServer zkTestServer;

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

    public void testWriteTlsMessage() throws MistException, InterruptedException, TimeoutException, CODIException, ClassNotFoundException, IllegalAccessException, InstantiationException, IOException, JMSException, JMSServiceException {
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

        TlsSender.reset();
        TlsSender.writeTlsMessage("test".getBytes(), new Exchange("tlsEx"), 5000);
        assertEquals("test", new String(brk.getMessage(true, "tlsEx")));

        brk.stop();
    }

    public static Test suite() {
        return new TestSuite(TestTlsSender.class);
    }
}
