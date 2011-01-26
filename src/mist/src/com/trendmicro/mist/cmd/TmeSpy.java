package com.trendmicro.mist.cmd;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import com.google.protobuf.TextFormat;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.DataListener;
import com.trendmicro.codi.DataObserver;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.codi.ZNode;
import com.trendmicro.codi.lock.ZLock;
import com.trendmicro.codi.lock.Lock.LockType;
import com.trendmicro.mist.BrokerSpy;
import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.proto.ZooKeeperInfo;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.TmeDBavatar;
import com.trendmicro.mist.util.TmeDBavatar.BrokerInfoData;
import com.trendmicro.spn.common.util.Utils;

public class TmeSpy implements DataListener {
    private static Log logger = LogFactory.getLog(TmeSpy.class);
    private static TmeSpy myApp;
    private int monInterval;
    private final int restartBrokerInterval = 300 * 1000;
    private boolean brokerDead = false;
    private int retValue = 0;
    private int alertCount = 0;
    private boolean timeToDie = false;
    private boolean brokerOffline = false;
    private boolean brokerOfflineChanged = false;
    private Thread mainThread;
    private BrokerSpy brokerSpy;
    private TmeDBavatar dbAvatar = null;
    private static String ZK_SERVER = Daemon.propMIST.getProperty("mistd.zookeeper");
    private static int ZK_TIMEOUT = Integer.valueOf(Daemon.propMIST.getProperty("mistd.zookeeper.timeout"));
    private static String ZK_NODE = "/tme2/broker/" + Utils.getHostIP();
    private ZNode brokerNode = null;
    private ZNode loadingNode = null;
    private DataObserver obs = null;

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                myApp.shutdown();
            }
        });
        logger.info("tme-spyd started");
    }

    private void shutdown() {
        timeToDie = true;
        removeBrokerNode();
        while(mainThread != null)
            Utils.justSleep(100);
        logger.info("tme-spyd shutdown");
    }

    private void addBrokerNode() {
        ZooKeeperInfo.Broker.Builder brkBuilder = ZooKeeperInfo.Broker.newBuilder();
        brkBuilder.setHost(brokerSpy.getBrokerHost());
        brkBuilder.setPort(brokerSpy.getBrokerPort());
        brkBuilder.setStatus(ZooKeeperInfo.Broker.Status.ONLINE);
        brkBuilder.addAccount(ZooKeeperInfo.Broker.Account.newBuilder().setUser("admin").setPassword("admin").build());
        brkBuilder.setBrokerType(brokerSpy.getBrokerType());
        brkBuilder.setVersion(brokerSpy.getBrokerVersion());
        brkBuilder.setReserved(false);
        ZooKeeperInfo.Broker broker = brkBuilder.build();

        ZooKeeperInfo.Loading load;
        for(;;) {
            try {
                load = brokerSpy.doSpy();
                break;
            }
            catch(Exception e) {
                logger.warn("getting memory usage failed, retry");
                logger.warn(Utils.convertStackTrace(e));
                Utils.justSleep(500);
            }
        }
        try {
            brokerNode.create(false, broker.toString().getBytes());
            obs.start();
        }
        catch(CODIException e) {
            logger.error(Utils.convertStackTrace(e));
        }
        try {
            loadingNode.create(false, load.toString().getBytes());
        }
        catch(CODIException e) {
            logger.error(Utils.convertStackTrace(e));
        }
        logger.info("addBrokerNode: " + ZK_NODE);
    }

    private void removeBrokerNode() {
        try {
            try {
                if(obs != null)
                    obs.stop();
                if(brokerNode != null)
                    brokerNode.deleteRecursively();
            }
            catch(CODIException e) {
                logger.error(Utils.convertStackTrace(e));
            }
            logger.info("removeBrokerNode: " + ZK_NODE);
        }
        catch(Exception e) {
            logger.error(e.toString());
        }
    }

    private void restartBrokerIfDead() {
        try {
            if(!brokerDead) {
                if(brokerDead = !checkBrokerAlive(5)) {
                    logger.error("cannot restart broker!");
                    removeBrokerNode();
                }
            }
            else {
                // broker is already dead for a while, entering lazy restart mode (5 min)
                logger.error("broker is dead, attempt to restart broker");
                brokerDead = !checkBrokerAlive(1);
                if(!brokerDead)
                    addBrokerNode();
                else
                    Utils.justSleep(restartBrokerInterval);
            }
        }
        catch(Exception e) {
            logger.error("restartBrokerIfDead() " + e.getMessage());
        }
    }

    private void startBroker() throws IOException {
        Process daemonProc = Runtime.getRuntime().exec(Daemon.propMIST.getProperty("spy.broker.command") + " start_broker");
        String line = new BufferedReader(new InputStreamReader(daemonProc.getInputStream())).readLine();
        logger.info(line);
        try {
            daemonProc.waitFor();
        }
        catch(InterruptedException e) {
        }
        daemonProc.getInputStream().close();
    }

    private void stopBroker() throws IOException {
        Process daemonProc = Runtime.getRuntime().exec(Daemon.propMIST.getProperty("spy.broker.command") + " stop_broker");
        String line = new BufferedReader(new InputStreamReader(daemonProc.getInputStream())).readLine();
        logger.info(line);
        try {
            daemonProc.waitFor();
        }
        catch(InterruptedException e) {
        }
        daemonProc.getInputStream().close();
    }

    private boolean checkBrokerAlive(int retryCount) throws IOException {
        String brokerClass = Daemon.propMIST.getProperty("spy.broker.classname");
        int brokerPid = Utils.getJavaProcessId(brokerClass);
        if(brokerPid == -1) {
            for(; retryCount-- > 0;) {
                logger.warn("broker not running! try to restart broker...");
                Runtime.getRuntime().exec(Daemon.propMIST.getProperty("spy.broker.alert.command")+" dead");
                startBroker();

                if((brokerPid = Utils.getJavaProcessId(brokerClass)) != -1) {
                    logger.info("broker successfully restarted");
                    setNewStatus(ZooKeeperInfo.Broker.Status.ONLINE);
                    Utils.justSleep(1000);
                    Runtime.getRuntime().exec(Daemon.propMIST.getProperty("spy.broker.alert.command")+" restart");
                    break;
                }
            }
            if(brokerPid == -1)
                return false;
        }
        return true;
    }

    private void setNewStatus(ZooKeeperInfo.Broker.Status status) {
        try {
            byte[] data = brokerNode.getContent();

            ZooKeeperInfo.Broker.Builder builder = ZooKeeperInfo.Broker.newBuilder();
            TextFormat.merge(new String(data), builder);
            builder.clearStatus();
            builder.setStatus(status);

            brokerNode.setContent(builder.build().toString().getBytes());
        }
        catch(Exception e) {
            logger.error("setNewStatus(): " + e.getMessage());
        }
    }

    private ZooKeeperInfo.Broker.Status getNewStatus() {
        try {
            byte[] data = brokerNode.getContent();
            ZooKeeperInfo.Broker.Builder builder = ZooKeeperInfo.Broker.newBuilder();
            TextFormat.merge(new String(data), builder);
            return builder.build().getStatus();
        }
        catch(Exception e) {
            logger.error("getNewStatus(): " + e.getMessage());
            return null;
        }
    }

    // //////////////////////////////////////////////////////////////////////////////

    public TmeSpy() {
        String namePidfile = "/var/run/tme/tme-spyd.pid";
        String nameLogfile = "/var/run/tme/tme-spyd.log";
        String pid = Utils.getCurrentPid();
        try {
            BufferedWriter pidfile = new BufferedWriter(new FileWriter(namePidfile));
            pidfile.write(pid);
            pidfile.newLine();
            pidfile.close();
            new File(namePidfile).deleteOnExit();

            Properties prop = new Properties();
            prop.load(new FileInputStream(System.getProperty("mistd.log.log4j", Daemon.nameLog4jConfig)));
            prop.setProperty("log4j.rootLogger", "OFF");
            prop.setProperty("log4j.logger.org.apache.zookeeper", "WARN, R");
            prop.setProperty("log4j.logger.com.trendmicro.mist", "INFO, R");
            prop.setProperty("log4j.appender.R.File", nameLogfile);
            PropertyConfigurator.configure(prop);
        }
        catch(IOException e) {
            logger.error("TmeSpy() " + e.getMessage());
        }
        ZKSessionManager.initialize(ZK_SERVER, ZK_TIMEOUT);
        try {
            ZKSessionManager.instance().waitConnected();
        }
        catch(InterruptedException e) {
            logger.error(Utils.convertStackTrace(e));
        }
        brokerNode = new ZNode(ZK_NODE);
        loadingNode = new ZNode(ZK_NODE + "/loading");
        obs = new DataObserver(ZK_NODE, this, false, 0);
        monInterval = Integer.valueOf(Daemon.propMIST.getProperty("spy.monitor.interval"));
        dbAvatar = TmeDBavatar.getInstance();
    }

    private synchronized boolean getAndTestBrokerOffline() {
        if(!brokerOfflineChanged)
            return brokerOffline;
        String lockPath = "/tme2/locks/brk_" + Utils.getHostIP();
        ZLock lock = new ZLock(lockPath);
        try {
            lock.acquire(LockType.WRITE_LOCK);
            ZooKeeperInfo.Broker.Status newStatus = getNewStatus();

            if((newStatus == ZooKeeperInfo.Broker.Status.OFFLINE) == brokerOffline) // status didn't change, return
                return brokerOffline;

            switch(newStatus) {
            case OFFLINE:
                brokerOffline = true;
                stopBroker();
                break;
            case ONLINE:
                brokerOffline = false;
                startBroker();
                break;
            default:
                break;
            }
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
        }
        finally {
            try {
                lock.release();
                if(lock.getChildren().isEmpty())
                    lock.delete();
            }
            catch(CODIException e) {
            }
            brokerOfflineChanged = false;
        }
        return brokerOffline;

    }

    public void start(String argv[]) throws Exception {
        while(!timeToDie) {                 // try to start broker first
            restartBrokerIfDead();
            if(!brokerDead){
                try{
                    brokerSpy = new BrokerSpy(
                        Daemon.propMIST.getProperty("spy.broker.type"),
                        Utils.getHostIP() + ":" + Daemon.propMIST.getProperty("spy.monitor.jmxport"),
                        Daemon.propMIST.getProperty("spy.monitor.jmxauth")
                    );
                    break;
                }
                catch(Exception e){
                    logger.error("retry: " + e.getMessage());
                }
            }
            Utils.justSleep(1000);
        }
        addBrokerNode();

        long lastReportTs = 0;
        long lastUpdateTs = 0;

        while(!timeToDie) {                 // monitoring main loop
            Utils.justSleep(1000);

            if(getAndTestBrokerOffline())
                continue;

            restartBrokerIfDead();
            if(!brokerDead) {
                long now = new Date().getTime();
                boolean isSpyBroker = now - lastReportTs > monInterval * 1000;
                boolean isUpdateDB = now - lastUpdateTs > TmeDBavatar.DB_UPDATE_INTERVAL;

                if(isSpyBroker) {
                    try {
                        ZooKeeperInfo.Loading load = brokerSpy.doSpy();
                        loadingNode.setContent(load.toString().getBytes());
                        int threshold = Integer.valueOf(Daemon.propMIST.getProperty("spy.broker.alert.threshold").trim());
                        if(load.getLoading() >= threshold) {
                            alertCount += 2;
                            // logger.info(String.format("Broker loading(%d) >= threshold(%d), count:%d",
                            // load.getLoading(), threshold, alertCount ));
                        }
                        else {
                            if(alertCount > 0)
                                alertCount--;
                        }
                        // Current alert will be issued for every 25 minute if
                        // loading is more than threshold continually
                        if(alertCount >= 100) {
                            alertCount = 0;
                            Runtime.getRuntime().exec(Daemon.propMIST.getProperty("spy.broker.alert.command") + " loading " + load.getLoading());
                            logger.info(String.format("Broker is overused against to threshold(%d)", threshold));
                        }
                        
                        // Check whether the exchange queue will be more than threshold
                        doSpyExchange();
                    }
                    catch(Exception e) {
                        logger.error("unable to retrieve broker info: " + e.getMessage());
                    }
                    lastReportTs = now;
                }

                if(isUpdateDB) {
                    updateDatabase();
                    lastUpdateTs = now;
                }
            }
        }
    }

    public static void main(String argv[]) {
        try {
            if(!Utils.checkSocketConnectable(ZK_SERVER))
                throw new Exception(String.format("unable to connect zookeeper `%s'", ZK_SERVER));

            myApp = new TmeSpy();
            myApp.addShutdownHook();
            myApp.mainThread = Thread.currentThread();
            myApp.start(argv);
            myApp.mainThread = null;
            System.exit(myApp.retValue);
        }
        catch(Exception e) {
            logger.fatal(e.getMessage());
        }
    }
    
    private void doSpyExchange() {
    	try {
            ArrayList<Exchange> allExchanges = brokerSpy.getAllExchangeMetadata();
   		
            for(Exchange em : allExchanges) {
                String exchangeName = em.getName();
                String exchangeType = em.isQueue() ? "q" : "t";
                try {
                    String pattern = String.format("com.sun.messaging.jms.server:type=Destination,subtype=Config,desttype=%s,name=\"%s\"", exchangeType, exchangeName);
                    brokerSpy.jmxConnectServer();
                    Map<String, String> mapConfig = brokerSpy.getMBeanAttributesMap(pattern, null);
                    long maxMsgNum = Long.parseLong(mapConfig.get("MaxNumMsgs"));
                    long maxMsgBytes = Long.parseLong(mapConfig.get("MaxTotalMsgBytes"));                    
                    pattern = String.format("com.sun.messaging.jms.server:type=Destination,subtype=Monitor,desttype=%s,name=\"%s\"", exchangeType, exchangeName);
                    Map<String, String> mapMonitor = brokerSpy.getMBeanAttributesMap(pattern, null);
                    long msg = Long.parseLong(mapMonitor.get("NumMsgs"));
                    long msgByte = Long.parseLong(mapMonitor.get("TotalMsgBytes"));                    
                    issueAlert(exchangeType + ":" + exchangeName, msg, msgByte, maxMsgNum, maxMsgBytes);                    
                }
                catch(Exception ex) {
                    logger.error(ex.getMessage());
                }
                finally {
                    brokerSpy.jmxCloseServer();
                }
    		}    		
    	} catch(Exception ex) {
            logger.error(ex.getMessage());
        }
    }

    private void updateDatabase() {
        synchronized(dbAvatar) {
            try {
                if(!dbAvatar.isConnectionReady())
                    return;
                // Insert Broker
                ZooKeeperInfo.Broker broker = brokerSpy.getBroker();
                if(-1 == dbAvatar.queryBrokerID(broker.getHost())) {
                    dbAvatar.insertBroker(broker);
                }
                Calendar rightNow = Calendar.getInstance();

                // Insert Exchange
                BrokerInfoData bid = getBrokerInfoData();
                bid.broker = broker.getHost();
                bid.timestamp = rightNow.getTimeInMillis();

                ArrayList<Exchange> allExchanges = brokerSpy.getAllExchangeMetadata();
                
                for(Exchange em : allExchanges) {
                    String exchangeName = em.getName();
                    String exchangeType = em.isQueue() ? "q" : "t";
                    try {
                        String pattern = String.format("com.sun.messaging.jms.server:type=Destination,subtype=Config,desttype=%s,name=\"%s\"", exchangeType, exchangeName);
                        brokerSpy.jmxConnectServer();
                        Map<String, String> mapConfig = brokerSpy.getMBeanAttributesMap(pattern, null);
                        long maxMsgNum = Long.parseLong(mapConfig.get("MaxNumMsgs"));
                        long maxMsgBytes = Long.parseLong(mapConfig.get("MaxTotalMsgBytes"));
                        if(-1 == dbAvatar.queryExchangeID(exchangeName, exchangeType)) {
                            dbAvatar.insertExchange(exchangeName, exchangeType, maxMsgNum, maxMsgBytes);
                        }
                        else {
                            dbAvatar.updateExchange(exchangeName, exchangeType, maxMsgNum, maxMsgBytes);
                        }
                    }
                    finally {
                        brokerSpy.jmxCloseServer();
                    }

                    // Query destination attributes
                    try {
                        String pattern = String.format("com.sun.messaging.jms.server:type=Destination,subtype=Monitor,desttype=%s,name=\"%s\"", exchangeType, exchangeName);
                        brokerSpy.jmxConnectServer();
                        Map<String, String> map = brokerSpy.getMBeanAttributesMap(pattern, null);
                        TmeDBavatar.ExchangeInfoData e = new TmeDBavatar.ExchangeInfoData();
                        e.name = exchangeName;
                        e.type = exchangeType;
                        e.host = broker.getHost();
                        e.AvgNumActiveConsumers = Integer.parseInt(map.get("AvgNumActiveConsumers"));
                        e.AvgNumBackupConsumers = Integer.parseInt(map.get("AvgNumBackupConsumers"));
                        e.AvgNumConsumers = Integer.parseInt(map.get("AvgNumConsumers"));
                        e.AvgNumMsgs = Long.parseLong(map.get("AvgNumMsgs"));
                        e.AvgTotalMsgBytes = Long.parseLong(map.get("AvgTotalMsgBytes"));
                        e.ConnectionID = map.get("ConnectionID");
                        e.DiskReserved = Long.parseLong(map.get("DiskReserved"));
                        e.DiskUsed = Long.parseLong(map.get("DiskUsed"));
                        e.DiskUtilizationRatio = Integer.parseInt(map.get("DiskUtilizationRatio"));
                        e.MsgBytesIn = Long.parseLong(map.get("MsgBytesIn"));
                        e.MsgBytesOut = Long.parseLong(map.get("MsgBytesOut"));
                        e.NextMessageID = map.get("NextMessageID");
                        e.NumActiveConsumers = Integer.parseInt(map.get("NumActiveConsumers"));
                        e.NumBackupConsumers = Integer.parseInt(map.get("NumBackupConsumers"));
                        e.NumConsumers = Integer.parseInt(map.get("NumConsumers"));
                        e.NumMsgs = Long.parseLong(map.get("NumMsgs"));
                        e.NumMsgsHeldInTransaction = Long.parseLong(map.get("NumMsgsHeldInTransaction"));
                        e.NumMsgsIn = Long.parseLong(map.get("NumMsgsIn"));
                        e.NumMsgsOut = Long.parseLong(map.get("NumMsgsOut"));
                        e.NumMsgsPendingAcks = Long.parseLong(map.get("NumMsgsPendingAcks"));
                        e.NumMsgsRemote = Long.parseLong(map.get("NumMsgsRemote"));
                        e.NumProducers = Integer.parseInt(map.get("NumProducers"));
                        e.NumWildcardConsumers = Integer.parseInt(map.get("NumWildcardConsumers"));
                        e.NumWildcardProducers = Integer.parseInt(map.get("NumWildcardProducers"));
                        e.NumWildcards = Integer.parseInt(map.get("NumWildcards"));
                        e.PeakMsgBytes = Long.parseLong(map.get("PeakMsgBytes"));
                        e.PeakNumMsgs = Long.parseLong(map.get("PeakNumMsgs"));
                        e.PeakTotalMsgBytes = Long.parseLong(map.get("PeakTotalMsgBytes"));
                        e.PeakNumActiveConsumers = Integer.parseInt(map.get("PeakNumActiveConsumers"));
                        e.PeakNumBackupConsumers = Integer.parseInt(map.get("PeakNumBackupConsumers"));
                        e.PeakNumConsumers = Integer.parseInt(map.get("PeakNumConsumers"));
                        e.State = Integer.parseInt(map.get("State"));
                        e.StateLabel = map.get("StateLabel");
                        e.Temporary = Boolean.parseBoolean(map.get("Temporary"));
                        e.TotalMsgBytes = Long.parseLong(map.get("TotalMsgBytes"));
                        e.TotalMsgBytesHeldInTransaction = Long.parseLong(map.get("TotalMsgBytesHeldInTransaction"));
                        e.TotalMsgBytesRemote = Long.parseLong(map.get("TotalMsgBytesRemote"));
                        e.timestamp = rightNow.getTimeInMillis();

                        TmeDBavatar.ExchangeInfoData pre_exc = dbAvatar.getLatestExchangeRec(broker.getHost(), exchangeName, exchangeType);

                        dbAvatar.insertExchangeRecord(e);

                        long in_diff, out_diff;
                        in_diff = out_diff = 0;

                        if (pre_exc != null) {
                        	in_diff = e.NumMsgsIn - pre_exc.NumMsgsIn;
                        	in_diff = in_diff < 0 ? e.NumMsgsIn : in_diff;

							out_diff = e.NumMsgsOut - pre_exc.NumMsgsOut;
							out_diff = out_diff < 0 ? e.NumMsgsOut : out_diff;

                        } else {
                        	in_diff = e.NumMsgsIn;
                        	out_diff = e.NumMsgsOut;
                        }

                        bid.inc_msg_in += in_diff;
                    	bid.inc_msg_out += out_diff;
                    	bid.total_msg_pendding += e.NumMsgs;

                        bid.total_consumer += e.NumConsumers;
                        bid.total_producer += e.NumProducers;

                        bid.num_exchange++;
                    }
                    finally {
                        brokerSpy.jmxCloseServer();
                    }
                }
                dbAvatar.insertBrokerRecord(bid);
            }
            catch(Exception ex) {
                logger.error(ex.getMessage());
            }
        }
    }
    
    private void issueAlert(String exchange, long msg, long msgByte, long maxMsg, long maxMsgByte) {    	
     	if (maxMsg != 0 && maxMsgByte != 0) {
    		float threshold = Float.valueOf(Daemon.propMIST.getProperty("spy.broker.alert.threshold").trim()) / 100.0f;
    		float msgRatio = (float)msg / maxMsg;
    		float byteRatio = (float)msgByte / maxMsgByte;    	
    		
    		if (msgRatio>threshold || byteRatio>threshold) {
    			try {
    				String content = String.format("The queue size of exchange %s is more than %.2f%% (now:%.2f%%)!", exchange, threshold*100, Math.max(msgRatio, byteRatio)*100);
    				ZNode smtp = new ZNode("/tme2/global/mail_smtp");
        			ZNode mail_list = new ZNode("/tme2/global/mail_alert");        			
        			String sh = String.format("echo \'%s\' | spn-mail --smtp=%s --to=\'%s\' --subject=%s",
        					content,
        					smtp.getContentString(), 
        					mail_list.getContentString(),
        					"\'[Alert]exchange queue will be full\'");   	
        			String[] cmd = {"/bin/sh", "-c", sh};
        			Runtime.getRuntime().exec(cmd);
    			} catch (CODIException ex) {
    				logger.error(ex.getMessage());
    			}catch (IOException ex) {
    				logger.error(ex.getMessage());
    			}    			
    		}
    	}
    }

    private BrokerInfoData getBrokerInfoData() throws Exception {
    	BrokerInfoData bid = new BrokerInfoData();

    	// Insert Broker record after exchange data update
        ZooKeeperInfo.Loading load = brokerSpy.doSpy();
        bid.max_mem = load.getMaxMemory();
        bid.free_mem = load.getFreeMemory();

		String[] args = new String[] { "sh", "-c", "ps ax | grep '[i]mqbroker.jar'" };
		java.lang.Process proc = Runtime.getRuntime().exec(args);
		BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		String line = in.readLine().trim();
		int pid = Integer.parseInt(Utils.splitIgnoreEmpty(line, " ")[0]);

		String cmd = "ps -o %cpu,%mem,etime -p " + pid + " | tail -1 | sed -e 's/[-:]/,/g'";
		args = new String[] { "sh", "-c", cmd };
		proc = Runtime.getRuntime().exec(args);
		in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		line = in.readLine().trim();
		String[] fields = Utils.splitIgnoreEmpty(line, " ");

		bid.cpu = (int)(Float.parseFloat(fields[0]) * 100);
		bid.mem = (int)(Float.parseFloat(fields[1]) * 100);

		String[] toks = fields[2].split(",");
		if (toks.length == 1)
			bid.up_sec = Integer.parseInt(toks[0]);
		else if (toks.length == 2) {
			bid.up_min = Integer.parseInt(toks[0]);
			bid.up_sec = Integer.parseInt(toks[1]);
		} else if (toks.length == 3) {
			bid.up_hour = Integer.parseInt(toks[0]);
			bid.up_min = Integer.parseInt(toks[1]);
			bid.up_sec = Integer.parseInt(toks[2]);
		} else if (toks.length == 4) {
			bid.up_date = Integer.parseInt(toks[0]);
			bid.up_hour = Integer.parseInt(toks[1]);
			bid.up_min = Integer.parseInt(toks[2]);
			bid.up_sec = Integer.parseInt(toks[3]);
		}
		//String s = String.format("PID:%d CPU:%d, MEM:%d, %d:%d:%d:%d", pid, bs.cpu, bs.mem, bs.up_date, bs.up_hour, bs.up_min, bs.up_sec);
		//logger.info(s);
		return bid;
    }

    @Override
    public synchronized void onDataChanged(String path, Map<String, byte[]> changeMap) {
        if(changeMap.get("") == null) // broker node is removed
            return;
        brokerOfflineChanged = true;
        logger.info("broker status changed");
    }
}
