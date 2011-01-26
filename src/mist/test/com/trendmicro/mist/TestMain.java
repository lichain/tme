package com.trendmicro.mist;

import com.trendmicro.mist.util.TestPacket;
import com.trendmicro.mist.util.TestConnectionList;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;

public class TestMain extends TestCase {
    public TestMain(String testMethod) {
        super(testMethod);
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(TestMain.suite());
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(TestPacket.class);
        suite.addTestSuite(TestConnectionList.class);
        return suite;
    }
}
