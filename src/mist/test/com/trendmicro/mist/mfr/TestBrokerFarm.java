package com.trendmicro.mist.mfr;

import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.codi.ZNode;
import com.trendmicro.mist.proto.ZooKeeperInfo;
import com.trendmicro.mist.util.ZKTestServer;
import com.trendmicro.tme.mfr.BrokerFarm;

public class TestBrokerFarm extends TestCase {
    private ZKTestServer zkTestServer = null;
    private long lastUpdateTs = 0;

    private void waitForUpdate(BrokerFarm brokerFarm) throws Exception {
        for(int i = 0; i < 10; i++) {
            long ts = brokerFarm.getLastUpdateTs();
            if(ts != lastUpdateTs) {
                lastUpdateTs = ts;
                return;
            }
            Thread.sleep(500);
        }
        throw new TimeoutException("Wait for the update from Zookeeper timed out!");
    }

    @Override
    protected void setUp() throws Exception {
        zkTestServer = new ZKTestServer(39977);
        zkTestServer.start();

        ZKSessionManager.initialize("localhost:39977", 8000);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        ZKSessionManager.uninitialize();
        zkTestServer.stop();
        super.tearDown();
    }

    public static ZooKeeperInfo.Broker genBrokerInfo(String host, Integer port, boolean online, String brokerType, boolean reserved) {
        ZooKeeperInfo.Broker.Builder builder = ZooKeeperInfo.Broker.newBuilder();
        builder.setHost(host);
        builder.setPort(port.toString());
        builder.setStatus(online ? ZooKeeperInfo.Broker.Status.ONLINE: ZooKeeperInfo.Broker.Status.OFFLINE);
        builder.addAccount(ZooKeeperInfo.Broker.Account.newBuilder().setUser("admin").setPassword("admin").build());
        builder.setBrokerType(brokerType);
        builder.setVersion("");
        builder.setReserved(reserved);
        return builder.build();
    }

    public static ZooKeeperInfo.Loading genBrokerLoading(Integer loading) {
        ZooKeeperInfo.Loading.Builder builder = ZooKeeperInfo.Loading.newBuilder();
        builder.setFreeMemory(0);
        builder.setLoading(loading);
        builder.setLastUpdate(0);
        builder.setMaxMemory(0);
        return builder.build();
    }

    public void testBrokerFarmConstructor() {
        BrokerFarm brokerFarm = new BrokerFarm();
        assertNotNull(brokerFarm);
    }

    public void testOnDataChanged() {
        BrokerFarm brokerFarm = new BrokerFarm();
        HashMap<String, byte[]> changeMap = new HashMap<String, byte[]>();

        /**
         * Test broker root node event, should ignore
         */
        changeMap.put("", "".getBytes());
        brokerFarm.onDataChanged("/broker", changeMap);
        assertTrue(brokerFarm.getAllBrokers().isEmpty());

        /**
         * Test add broker
         */
        changeMap.clear();
        ZooKeeperInfo.Broker brkNode = genBrokerInfo("127.0.0.1", 7676, true, "OpenMQ", false);
        changeMap.put("127.0.0.1", brkNode.toString().getBytes());
        brokerFarm.onDataChanged("/broker", changeMap);
        assertEquals(brkNode, brokerFarm.getBrokerByHost("127.0.0.1"));

        /**
         * Test add loading
         */
        changeMap.clear();
        ZooKeeperInfo.Loading loadingNode = genBrokerLoading(10);
        changeMap.put("127.0.0.1/loading", loadingNode.toString().getBytes());
        brokerFarm.onDataChanged("/broker", changeMap);
        assertEquals(brkNode, brokerFarm.getBrokerByHost("127.0.0.1"));
        assertEquals(loadingNode, brokerFarm.getAllLoading().get("127.0.0.1"));
        assertEquals(1, brokerFarm.getBrokerCount());

        /**
         * Test remove loading
         */
        changeMap.clear();
        changeMap.put("127.0.0.1/loading", null);
        brokerFarm.onDataChanged("/broker", changeMap);
        assertTrue(brokerFarm.getAllLoading().isEmpty());

        /**
         * Test remove broker
         */
        changeMap.clear();
        changeMap.put("127.0.0.1", null);
        brokerFarm.onDataChanged("/broker", changeMap);
        assertTrue(brokerFarm.getAllBrokers().isEmpty());
        assertEquals(0, brokerFarm.getBrokerCount());

        /**
         * Test add multiple nodes
         */
        changeMap.clear();
        changeMap.put("127.0.0.1", brkNode.toString().getBytes());
        changeMap.put("127.0.0.1/loading", loadingNode.toString().getBytes());
        brokerFarm.onDataChanged("/broker", changeMap);
        assertEquals(brkNode, brokerFarm.getBrokerByHost("127.0.0.1"));
        assertEquals(loadingNode, brokerFarm.getAllLoading().get("127.0.0.1"));
        assertEquals(1, brokerFarm.getBrokerCount());

        /**
         * Test remove multiple nodes
         */
        changeMap.clear();
        changeMap.put("127.0.0.1", null);
        changeMap.put("127.0.0.1/loading", null);
        brokerFarm.onDataChanged("/broker", changeMap);
        assertTrue(brokerFarm.getAllBrokers().isEmpty());
        assertTrue(brokerFarm.getAllLoading().isEmpty());
        assertEquals(0, brokerFarm.getBrokerCount());
    }

    public void testUpdateFromZookeeper() throws Exception {
        BrokerFarm brokerFarm = new BrokerFarm();
        lastUpdateTs = brokerFarm.getLastUpdateTs();

        /**
         * Test add 1 broker node
         */
        ZooKeeperInfo.Broker brk = genBrokerInfo("127.0.0.1", 7676, true, "OpenMQ", false);
        ZNode broker1Node = new ZNode("/broker" + "/127.0.0.1");
        broker1Node.create(false, brk.toString().getBytes());
        waitForUpdate(brokerFarm);
        assertEquals(brk, brokerFarm.getAllBrokers().get("127.0.0.1"));
        assertEquals(1, brokerFarm.getBrokerCount());

        /**
         * Test add another broker node
         */
        ZooKeeperInfo.Broker brk2 = genBrokerInfo("127.0.0.2", 7676, true, "OpenMQ", false);
        ZNode broker2Node = new ZNode("/broker" + "/127.0.0.2");
        broker2Node.create(false, brk2.toString().getBytes());
        waitForUpdate(brokerFarm);
        assertEquals(brk2, brokerFarm.getAllBrokers().get("127.0.0.2"));
        assertEquals(2, brokerFarm.getBrokerCount());

        /**
         * Test add loading node
         */
        ZooKeeperInfo.Loading loading = genBrokerLoading(10);
        ZNode loading1Node = new ZNode("/broker" + "/127.0.0.1/loading");
        loading1Node.create(false, loading.toString().getBytes());
        waitForUpdate(brokerFarm);
        assertEquals(loading, brokerFarm.getAllLoading().get("127.0.0.1"));

        /**
         * Test add another loading node
         */
        ZooKeeperInfo.Loading loading2 = genBrokerLoading(10);
        ZNode loading2Node = new ZNode("/broker" + "/127.0.0.2/loading");
        loading2Node.create(false, loading.toString().getBytes());
        waitForUpdate(brokerFarm);
        assertEquals(loading2, brokerFarm.getAllLoading().get("127.0.0.2"));

        /**
         * Test remove broker1
         */
        loading1Node.delete();
        waitForUpdate(brokerFarm);
        broker1Node.delete();
        waitForUpdate(brokerFarm);
        assertNull(brokerFarm.getAllBrokers().get("127.0.0.1"));
        assertNull(brokerFarm.getAllLoading().get("127.0.0.1"));
        assertEquals(1, brokerFarm.getBrokerCount());

        /**
         * Test remove broker2
         */
        loading2Node.delete();
        waitForUpdate(brokerFarm);
        broker2Node.delete();
        waitForUpdate(brokerFarm);
        assertTrue(brokerFarm.getAllBrokers().isEmpty());
        assertTrue(brokerFarm.getAllLoading().isEmpty());
    }

    public static Test suite() {
        return new TestSuite(TestBrokerFarm.class);
    }
}
