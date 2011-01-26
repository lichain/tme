package com.trendmicro.mist.util;

import junit.framework.TestCase;

public class TestConnectionList extends TestCase {
    public TestConnectionList(String testMethod) {
        super(testMethod);
    }
    
    public void testSet() {
        ConnectionList conn_list = new ConnectionList();
        
        conn_list.set("  host1:port1  ");
        assertEquals(conn_list.toString(), "host1:port1");
        conn_list.set("host1:port1");
        assertEquals(conn_list.toString(), "host1:port1");
        conn_list.set("host1");
        assertEquals(conn_list.toString(), "host1");
        conn_list.set("host1:");
        assertEquals(conn_list.toString(), "host1");
        conn_list.set(":port1");
        assertEquals(conn_list.toString(), ":port1");
        conn_list.set(":");
        assertEquals(conn_list.toString(), "");
        conn_list.set("");
        assertEquals(conn_list.toString(), "");
        
        conn_list.set("host1:port1,host2:port2");
        assertEquals(conn_list.toString(), "host1:port1,host2:port2");
        conn_list.set("host1:port1,");
        assertEquals(conn_list.toString(), "host1:port1");
        conn_list.set("host1:port1,,host2:port2");
        assertEquals(conn_list.toString(), "host1:port1,,host2:port2");
        conn_list.set(",host1:port1");
        assertEquals(conn_list.toString(), ",host1:port1");
    }

    public void testAdd() {
        ConnectionList conn_list = new ConnectionList();
        conn_list.set("host1,host2:port2");
        assertEquals(conn_list.toString(), "host1,host2:port2");
        conn_list.add("host3");
        assertEquals(conn_list.toString(), "host1,host2:port2,host3");
    }
    
    public void testMerge() {
        ConnectionList conn_list = new ConnectionList();
        conn_list.set("host1:port1");
        conn_list.merge("host2:port2,host3:port3");
        assertEquals(conn_list.toString(), "host1:port1,host2:port2,host3:port3");
    }
    
    public void testGet() {
        ConnectionList conn_list = new ConnectionList();
        conn_list.set("host1:80,host2:81");
        assertEquals(conn_list.toString(), "host1:80,host2:81");
        conn_list.add("host3:21");
        assertEquals(conn_list.get(0).getPort(), "80");
        assertEquals(conn_list.get(1).getHost(), "host2");
        assertEquals(conn_list.get(2).getPort(), "21");
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(TestConnectionList.class);
    }
}
