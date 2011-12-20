package com.trendmicro.tme.broker;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.messaging.jmq.jmsclient.runtime.BrokerInstance;
import com.sun.messaging.jmq.jmsclient.runtime.ClientRuntime;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.codi.ZNode;
import com.trendmicro.mist.proto.ZooKeeperInfo;

public class EmbeddedOpenMQ {
    private final static Logger logger = LoggerFactory.getLogger(EmbeddedOpenMQ.class);
    private BrokerInstance brokerInstance;
    private Properties config;
    private String host;
    
    public EmbeddedOpenMQ(String mqHome, String mqVar, Properties config) throws Exception {
        host = InetAddress.getLocalHost().getHostAddress();
        
        ClientRuntime clientRuntime = ClientRuntime.getRuntime();
        brokerInstance = clientRuntime.createBrokerInstance();
        
        this.config = brokerInstance.parseArgs(new String[] {
            "-imqhome", mqHome, "-varhome", mqVar
        });
        this.config.putAll(config);
        
        brokerInstance.init(this.config, null);
    }
    
    public void start() {
        logger.info("Starting OpenMQ Broker...");
        brokerInstance.start();
        logger.info("OpenMQ Broker started!");
    }
    
    public void stop() {
        logger.info("Stopping OpenMQ Broker...");
        brokerInstance.stop();
        logger.info("Shutting down OpenMQ Broker...");
        brokerInstance.shutdown();
        logger.info("Bye!");
    }
    
    public void registerOnZk() throws CODIException, UnknownHostException {
        ZooKeeperInfo.Broker.Builder brkBuilder = ZooKeeperInfo.Broker.newBuilder();
        brkBuilder.setHost(host);
        brkBuilder.setPort(config.getProperty("imq.portmapper.port"));
        brkBuilder.setStatus(ZooKeeperInfo.Broker.Status.ONLINE);
        brkBuilder.addAccount(ZooKeeperInfo.Broker.Account.newBuilder().setUser("admin").setPassword("admin").build());
        brkBuilder.setBrokerType("openmq");
        brkBuilder.setVersion("4.5.1");
        brkBuilder.setReserved(false);
        
        ZNode brkNode = new ZNode("/tme2/broker/" + host);
        try {
            brkNode.deleteRecursively();
        }
        catch(Exception e) {
        }
        brkNode.create(false, brkBuilder.build().toString());
        
        ZNode loadingNode = new ZNode("/tme2/broker/" + host + "/loading");
        loadingNode.create(false, ZooKeeperInfo.Loading.newBuilder().setLoading(0).setLastUpdate(0).setFreeMemory(0).setMaxMemory(0).build().toString());
    }
    
    public void unregisterOnZk() throws CODIException {
        ZNode brkNode = new ZNode("/tme2/broker/" + host);
        brkNode.deleteRecursively();
    }
    
    public void reportLoadingForever() throws InterruptedException {
        ZNode loadingNode = new ZNode("/tme2/broker/" + host + "/loading");
        while(true) {
            long free = Runtime.getRuntime().freeMemory();
            long total = Runtime.getRuntime().totalMemory();
            long max = Runtime.getRuntime().maxMemory();
            
            ZooKeeperInfo.Loading.Builder load_builder = ZooKeeperInfo.Loading.newBuilder();
            load_builder.setLoading(Math.round(((float) (max - free) / max) * 100));
            load_builder.setLastUpdate(new Date().getTime());
            load_builder.setFreeMemory(free);
            load_builder.setMaxMemory(max);
            try {
                loadingNode.setContent(load_builder.build().toString());
                logger.info(String.format("Reporting loading: free memory: %d, total memory: %d max memory: %d", free, total, max));
            }
            catch(CODIException e) {
                logger.error("Cannot report loading", e);
            }
            Thread.sleep(30000);            
        }
    }
    
    public static void main(String argv[]) throws Exception {
        String mqHome = System.getProperty("com.trendmicro.tme.broker.mqhome", "/opt/trend/tme");
        String mqVar = System.getProperty("com.trendmicro.tme.broker.mqvar", "/var/lib/tme/broker");
        String configPath = System.getProperty("com.trendmicro.tme.broker.config", "/opt/trend/tme/conf/broker/config.properties");
        
        Properties config = new Properties();
        FileInputStream fis = new FileInputStream(configPath);
        config.load(fis);
        IOUtils.closeQuietly(fis);
        
        ZKSessionManager.initialize(config.getProperty("com.trendmicro.tme.broker.zookeeper.quorum"), Integer.valueOf(config.getProperty("com.trendmicro.tme.broker.zookeeper.timeout")));
        
        final EmbeddedOpenMQ mq = new EmbeddedOpenMQ(mqHome, mqVar, config);
        mq.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    mq.unregisterOnZk();
                }
                catch(CODIException e) {
                    logger.error("Unable to unregister broker", e);
                }
                mq.stop();                
            }
        });
        
        mq.registerOnZk();
        mq.reportLoadingForever();
    }
}
