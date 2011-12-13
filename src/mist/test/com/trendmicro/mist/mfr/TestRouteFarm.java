package com.trendmicro.mist.mfr;

import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.TimeoutException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.codi.ZNode;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.ZKTestServer;

public class TestRouteFarm extends TestCase {
    private ZKTestServer zkTestServer = null;
    private long lastUpdateTs;

    /**
     * Wait for the routeFarm receive the update from Zookeeper, the timeout is
     * 5 secs
     * 
     * @param routeFarm
     *            The routeFarm instance to wait for
     * @throws Exception
     *             Throws a TimeoutException when routeFarm didn't receive
     *             update in 5 secs
     */
    private void waitForUpdate(RouteFarm routeFarm) throws Exception {
        for(int i = 0; i < 10; i++) {
            long ts = routeFarm.getLastUpdateTs();
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

    public void testRouteFarmConstructor() throws Exception {
        RouteFarm routeFarm = RouteFarm.getInstance();
        assertNotNull(routeFarm);
    }

    public void testGetDestList() {
        RouteFarm routeFarm = RouteFarm.getInstance();
        routeFarm.reset();
        /**
         * Insert a routing rule with foo.out->bar.in,log.in
         */
        Vector<Exchange> destVec = new Vector<Exchange>();
        destVec.add(new Exchange("bar.in"));
        destVec.add(new Exchange("bar.in"));
        routeFarm.getRouteTable().put("foo.out", destVec);

        /**
         * getDestList will return a cloned destList, so they should not be the
         * same object, but their content should be equal
         */
        assertNotSame(destVec, routeFarm.getDestList("foo"));
        for(Exchange dst : destVec)
            assertTrue(routeFarm.getDestList("foo.out").contains(dst));
        for(Exchange dst : routeFarm.getDestList("foo.out"))
            assertTrue(destVec.contains(dst));

        /**
         * Given a name not in the table should return null
         */
        assertNull(routeFarm.getDestList("bar.out"));
    }

    /**
     * Simulates the incoming events from Zookeeper when node changes
     */
    public void testOnDataChanged() {
        RouteFarm routeFarm = RouteFarm.getInstance();
        routeFarm.reset();
        HashMap<String, byte[]> changeMap = new HashMap<String, byte[]>();

        /**
         * The graph root node created event, the routing table should be empty
         */
        changeMap.put("", "".getBytes());
        routeFarm.onDataChanged(RouteFarm.graphRoot, changeMap);
        assertTrue(routeFarm.getRouteTable().isEmpty());

        /**
         * The wireit data of a graph is saved event, RouteFarm should ignore it
         * and the table remains empty
         */
        changeMap.clear();
        changeMap.put("foo/wireit", "".getBytes());
        routeFarm.onDataChanged(RouteFarm.graphRoot, changeMap);
        assertTrue(routeFarm.getRouteTable().isEmpty());

        /**
         * An empty graph is created, the routing table should remain empty
         */
        changeMap.clear();
        changeMap.put("foo", "".getBytes());
        routeFarm.onDataChanged(RouteFarm.graphRoot, changeMap);
        assertTrue(routeFarm.getRouteTable().isEmpty());

        /**
         * A forwarding rule from foo.out to bar.in is created
         */
        changeMap.clear();
        changeMap.put("foo", "foo.out-bar.in".getBytes());
        routeFarm.onDataChanged(RouteFarm.graphRoot, changeMap);
        assertNotNull(routeFarm.getDestList("foo.out"));
        assertTrue(routeFarm.getDestList("foo.out").contains("bar.in"));

        /**
         * Drop the message from bar.out
         */
        changeMap.clear();
        changeMap.put("foo", "foo.out-bar.in\nbar.out-".getBytes());
        routeFarm.onDataChanged(RouteFarm.graphRoot, changeMap);
        assertNotNull(routeFarm.getDestList("foo.out"));
        assertNotNull(routeFarm.getDestList("bar.out"));
        assertTrue(routeFarm.getDestList("foo.out").contains("bar.in"));
        assertTrue(routeFarm.getDestList("bar.out").contains(""));

        /**
         * A forwarding rule from foo.out to log.in is created in another graph
         */
        changeMap.clear();
        changeMap.put("log", "foo.out-log.in".getBytes());
        changeMap.put("foo", "foo.out-bar.in\nbar.out-".getBytes());
        routeFarm.onDataChanged(RouteFarm.graphRoot, changeMap);
        assertNotNull(routeFarm.getDestList("foo.out"));
        assertNotNull(routeFarm.getDestList("bar.out"));
        assertTrue(routeFarm.getDestList("foo.out").contains("bar.in"));
        assertTrue(routeFarm.getDestList("bar.out").contains(""));
        assertTrue(routeFarm.getDestList("foo.out").contains("log.in"));

        /**
         * Remove the graph log
         */
        changeMap.clear();
        changeMap.put("log", null);
        routeFarm.onDataChanged(RouteFarm.graphRoot, changeMap);
        assertNotNull(routeFarm.getDestList("foo.out"));
        assertNotNull(routeFarm.getDestList("bar.out"));
        assertTrue(routeFarm.getDestList("foo.out").contains("bar.in"));
        assertTrue(routeFarm.getDestList("bar.out").contains(""));

        /**
         * Remove the graph foo
         */
        changeMap.clear();
        changeMap.put("foo", null);
        routeFarm.onDataChanged(RouteFarm.graphRoot, changeMap);
        assertTrue(routeFarm.getRouteTable().isEmpty());
    }

    /**
     * Actually update the node on Zookeeper
     * 
     * @throws Exception
     */
    public void testUpdateFromZookeeper() throws Exception {
        RouteFarm routeFarm = RouteFarm.getInstance();
        routeFarm.reset();
        lastUpdateTs = routeFarm.getLastUpdateTs();

        /**
         * Create an empty graph called foo
         */
        ZNode fooNode = new ZNode(RouteFarm.graphRoot + "/foo");
        fooNode.create(false, "".getBytes());
        waitForUpdate(routeFarm);
        assertTrue(routeFarm.getRouteTable().isEmpty());

        /**
         * Insert a forwarding rule in foo
         */
        fooNode.setContent("foo.out-log.in".getBytes());
        waitForUpdate(routeFarm);
        assertNotNull(routeFarm.getDestList("foo.out"));
        assertTrue(routeFarm.getDestList("foo.out").contains("log.in"));

        /**
         * Add a forwarding rule in foo
         */
        fooNode.setContent("foo.out-log.in\nfoo.out-bar.in".getBytes());
        waitForUpdate(routeFarm);
        assertNotNull(routeFarm.getDestList("foo.out"));
        assertTrue(routeFarm.getDestList("foo.out").contains("log.in"));
        assertTrue(routeFarm.getDestList("foo.out").contains("bar.in"));

        /**
         * Add another graph
         */
        ZNode barNode = new ZNode(RouteFarm.graphRoot + "/bar");
        barNode.create(false, "bar.out-");
        waitForUpdate(routeFarm);
        assertNotNull(routeFarm.getDestList("foo.out"));
        assertNotNull(routeFarm.getDestList("bar.out"));
        assertTrue(routeFarm.getDestList("bar.out").contains(""));
        assertTrue(routeFarm.getDestList("foo.out").contains("log.in"));
        assertTrue(routeFarm.getDestList("foo.out").contains("bar.in"));

        /**
         * Remove graph foo
         */
        fooNode.deleteRecursively();
        waitForUpdate(routeFarm);
        assertNull(routeFarm.getDestList("foo.out"));
        assertNotNull(routeFarm.getDestList("bar.out"));
        assertTrue(routeFarm.getDestList("bar.out").contains(""));

        /**
         * Remove graph bar
         */
        barNode.deleteRecursively();
        waitForUpdate(routeFarm);
        assertTrue(routeFarm.getRouteTable().isEmpty());
    }

    public static Test suite() {
        return new TestSuite(TestRouteFarm.class);
    }
}
