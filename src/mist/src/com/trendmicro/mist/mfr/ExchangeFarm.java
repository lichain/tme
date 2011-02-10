package com.trendmicro.mist.mfr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.DataListener;
import com.trendmicro.codi.DataObserver;
import com.trendmicro.codi.Node;
import com.trendmicro.codi.NodeListener;
import com.trendmicro.codi.ZNode;
import com.trendmicro.mist.BrokerAdmin;
import com.trendmicro.mist.BrokerSpy;
import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.proto.ZooKeeperInfo;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.spn.common.util.Utils;

public class ExchangeFarm extends Thread implements DataListener {
    private static Log logger = LogFactory.getLog(ExchangeFarm.class);
    private static ExchangeFarm m_theSingleton = null;
    private static final String GOC_NODE = "/tme2/global/goc_exchange";
    private static final String TLS_NODE = "/tme2/global/tls_exchange";
    private static final String FIXED_NODE = "/tme2/global/fixed_exchange";
    private static final ZooKeeperInfo.Reference refdata = ZooKeeperInfo.Reference.newBuilder().setHost(Utils.getHostIP()).build();

    private HashSet<String> gocExchanges = new HashSet<String>();
    private HashMap<String, ZooKeeperInfo.TLSConfig> tlsExchanges = new HashMap<String, ZooKeeperInfo.TLSConfig>();
    private HashMap<String, String> fixedExchanges = new HashMap<String, String>();

    private HashMap<String, HashSet<ZNode>> allExchangeRefs = new HashMap<String, HashSet<ZNode>>();
    private LinkedBlockingDeque<ExchangeEvent> exchangeEventQueue = new LinkedBlockingDeque<ExchangeEvent>();

    private DataObserver gocObs = null;
    private DataObserver tlsObs = null;
    private DataObserver fixedObs = null;

    class ExchangeEvent {
    }

    class TlsConfigEvent extends ExchangeEvent {
        public Exchange exchange = new Exchange();
        public ZooKeeperInfo.TLSConfig tlsConfig = null;
    }

    class RefListener extends ExchangeEvent implements NodeListener {
        private ZNode origNode = null;
        private Exchange exchange = null;
        private String refRoot = null;

        public RefListener(ZNode origNode, Exchange exchange, String refRoot) {
            this.origNode = origNode;
            this.exchange = exchange;
            this.refRoot = refRoot;
        }

        @Override
        public boolean onChildrenChanged(Node arg0) {
            return false;
        }

        @Override
        public boolean onConnected() {
            return false;
        }

        @Override
        public boolean onDataChanged(Node arg0) {
            return false;
        }

        @Override
        public boolean onDisconnected() {
            return true;
        }

        @Override
        public boolean onNodeCreated(Node arg0) {
            return false;
        }

        @Override
        public boolean onNodeDeleted(Node arg0) {
            return false;
        }

        @Override
        public boolean onSessionExpired() {
            logger.info("ref node " + origNode.getPath() + " expired");
            try {
                exchangeEventQueue.put(this);
            }
            catch(InterruptedException e) {
                logger.error(Utils.convertStackTrace(e));
            }
            return false;
        }

        public void renewRef() {
            synchronized(allExchangeRefs) {
                ZNode newRefNode = genRefNode(refRoot);
                HashSet<ZNode> refs = allExchangeRefs.get(exchange.toString());
                refs.remove(origNode);
                refs.add(newRefNode);
                newRefNode.setNodeListener(new RefListener(newRefNode, exchange, refRoot));
                logger.info("new ref node created: " + newRefNode.getPath());
            }
        }
    }

    private ExchangeFarm() {
        gocObs = new DataObserver(GOC_NODE, this, true, 0);
        tlsObs = new DataObserver(TLS_NODE, this, true, 0);
        fixedObs = new DataObserver(FIXED_NODE, this, true, 0);

        gocObs.start();
        tlsObs.start();
        fixedObs.start();
        new Thread(this).start();
    }

    private String queryFixedExchange(String conceptName) {
        String fixed_host = null;
        for(Map.Entry<String, String> e : fixedExchanges.entrySet()) {
            String exchange = e.getKey();
            if(exchange.equals(conceptName)) {
                fixed_host = e.getValue();
                break;
            }
        }
        if(fixed_host == null)
            return null;

        String hostname = null;
        ZooKeeperInfo.Broker broker = BrokerFarm.getInstance().getBrokerByHost(fixed_host);
        if(broker != null) {
            if(BrokerFarm.checkConnectable(broker)) {
                hostname = fixed_host;
                logger.info(String.format("fixed exchange: %s @ %s", conceptName, fixed_host));
            }
            else
                logger.warn(String.format("fixed exchange: %s, but %s not connectable", conceptName, fixed_host));
        }
        else
            logger.warn(String.format("fixed exchange: %s, but %s not found in broker farm", conceptName, fixed_host));

        return hostname;
    }

    private String decideExchangeHost(String name) {
        if(BrokerFarm.getInstance().getBrokerCount() == 0)
            return null;

        Vector<Vector<String>> loadingScaleVec = new Vector<Vector<String>>();
        for(int i = 0; i < 10; i++) {
            Vector<String> brokers = new Vector<String>();
            loadingScaleVec.add(brokers);
        }

        Map<String, ZooKeeperInfo.Loading> loadingMap = BrokerFarm.getInstance().getAllLoading();

        for(Entry<String, ZooKeeperInfo.Loading> e : loadingMap.entrySet())
            loadingScaleVec.get(e.getValue().getLoading() / 10).add(e.getKey());

        Vector<String> reserved = new Vector<String>();
        for(Vector<String> brokers : loadingScaleVec) {
            if(brokers.isEmpty())
                continue;
            ZooKeeperInfo.Broker b;
            do {
                // select a broker in the same loading scale by exchange name's
                // hash value
                int idx = Math.abs(name.hashCode()) % brokers.size();
                b = BrokerFarm.getInstance().getBrokerByHost(brokers.get(idx));
                if(BrokerFarm.checkConnectable(b)) {
                    if(b.getReserved()) {
                        reserved.add(b.getHost());
                        brokers.remove(idx);
                    }
                    else {
                        logger.info(String.format("decideExchangeHost(%s) return %s", name, b.getHost()));
                        return b.getHost();
                    }
                }
                else {
                    logger.warn(String.format("decideExchangeHost() broker %s is not connectable", b.getHost()));
                    brokers.remove(idx);
                }
            } while(brokers.size() > 0);
        }
        if(reserved.size() > 0) {
            logger.warn(String.format("no broker available, use reserved broker `%s'", reserved.get(0)));
            return reserved.get(0);
        }
        return null;
    }

    private void updateGocExchanges(Map<String, byte[]> changeMap) {
        for(Entry<String, byte[]> ent : changeMap.entrySet()) {
            if(ent.getKey().length() == 0)
                continue;
            if(ent.getValue() == null)
                gocExchanges.remove(ent.getKey());
            else
                gocExchanges.add(ent.getKey());
        }
    }

    private void updateTlsExchanges(Map<String, byte[]> changeMap) {
        for(Entry<String, byte[]> ent : changeMap.entrySet()) {
            if(ent.getKey().length() == 0)
                continue;
            TlsConfigEvent ev = new TlsConfigEvent();
            ev.exchange = new Exchange(ent.getKey());
            if(ent.getValue() != null) {
                try {
                    ZooKeeperInfo.TLSConfig.Builder tlsCfgBuilder = ZooKeeperInfo.TLSConfig.newBuilder();
                    TextFormat.merge(new String(ent.getValue()), tlsCfgBuilder);
                    ev.tlsConfig = tlsCfgBuilder.build();
                }
                catch(Exception e) {
                    logger.error(Utils.convertStackTrace(e));
                    continue;
                }
            }
            try {
                exchangeEventQueue.put(ev);
            }
            catch(InterruptedException e) {
                logger.error(Utils.convertStackTrace(e));
            }
        }
    }

    private void updateFixedExchanges(Map<String, byte[]> changeMap) {
        for(Entry<String, byte[]> ent : changeMap.entrySet()) {
            if(ent.getKey().length() == 0)
                continue;
            fixedExchanges.remove(ent.getKey());
            if(ent.getValue() != null) {
                ZooKeeperInfo.Exchange.Builder builder = ZooKeeperInfo.Exchange.newBuilder();
                try {
                    TextFormat.merge(new String(ent.getValue()), builder);
                    String host = builder.build().getHost();
                    fixedExchanges.put(ent.getKey(), host);
                }
                catch(ParseException e) {
                    logger.error("cannot update fixed exchange " + ent.getKey() + ": " + new String(ent.getValue()));
                }
            }
        }
    }

    private boolean hasPendingMessage(Exchange exchange) {
        ZooKeeperInfo.Broker brk = BrokerFarm.getInstance().getBrokerByHost(exchange.getBroker());
        if(brk == null) {
            logger.warn("hasPendingMessage(): " + exchange.getBroker() + " does not exist");
            return false;
        }
        else if(!BrokerFarm.checkConnectable(brk)) {
            logger.warn("hasPendingMessage(): " + exchange.getBroker() + " is not connectable");
            return false;
        }

        try {
            BrokerSpy spy = new BrokerSpy(Daemon.propMIST.getProperty("spy.broker.type"), exchange.getBroker() + ":" + Daemon.propMIST.getProperty("spy.monitor.jmxport"), Daemon.propMIST.getProperty("spy.monitor.jmxauth"));
            String pattern = String.format("com.sun.messaging.jms.server:type=Destination,subtype=Monitor,desttype=%s,name=\"%s\"", exchange.isQueue() ? "q": "t", exchange.getName());
            spy.jmxConnectServer();
            Map<String, String> map = spy.getMBeanAttributesMap(pattern, null);
            spy.jmxCloseServer();
            return(Integer.parseInt(map.get("NumMsgs")) > 0);
        }
        catch(Exception e) {
            logger.error(e.toString());
            return true;
        }
    }

    private boolean hasReference(Exchange exchange) {
        try {
            String path = "/tme2/exchange/" + exchange.toString();
            ZNode node = new ZNode(path);
            boolean no_ref = ((!node.exists()) || (node.getChildren().size() == 0));
            return !no_ref;
        }
        catch(Exception e) {
            logger.warn(e.toString());
            return true;
        }
    }

    private ZNode genRefNode(String refRoot) {
        for(int i = 0; i < 3; i++) {
            try {
                return ZNode.createSequentialNode(refRoot + "/ref", true, refdata.toString().getBytes());
            }
            catch(CODIException e) {
                logger.error("cannot generate reference node under " + refRoot);
                logger.fatal(Utils.convertStackTrace(e));
                logger.info("retrying...");
            }
        }
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////

    public static ExchangeFarm getInstance() {
        if(null == m_theSingleton)
            m_theSingleton = new ExchangeFarm();
        return m_theSingleton;
    }

    public void reset() {
        gocExchanges.clear();
        tlsExchanges.clear();
        fixedExchanges.clear();
        allExchangeRefs.clear();
        exchangeEventQueue.clear();

        gocObs = new DataObserver(GOC_NODE, this, true, 0);
        tlsObs = new DataObserver(TLS_NODE, this, true, 0);
        fixedObs = new DataObserver(FIXED_NODE, this, true, 0);

        gocObs.start();
        tlsObs.start();
        fixedObs.start();
    }

    public void run() {
        Thread.currentThread().setName("ExchangeEventHandler");
        for(;;) {
            try {
                ExchangeEvent ev = exchangeEventQueue.take();
                if(ev instanceof RefListener)
                    ((RefListener) ev).renewRef();
                //TODO: check if this is not needed
/*                else if(ev instanceof TlsConfigEvent) {
                    TlsConfigEvent tlsEv = (TlsConfigEvent) ev;
                    if(tlsExchanges.remove(tlsEv.exchange.toString()) != null) {
                        synchronized(Daemon.sessionPool) {
                            for(Session sess : Daemon.sessionPool)
                                sess.removeTlsClient(tlsEv.exchange);
                        }
                    }
                    if(tlsEv.tlsConfig != null) {
                        tlsExchanges.put(tlsEv.exchange.toString(), tlsEv.tlsConfig);
                        synchronized(Daemon.sessionPool) {
                            for(Session sess : Daemon.sessionPool) {
                                Client c = sess.findClient(tlsEv.exchange);
                                if(c != null)
                                    sess.addTlsClient(c, tlsEv.tlsConfig);
                            }
                        }
                    }
                }*/
            }
            catch(InterruptedException e) {
                continue;
            }
        }
    }

    public String incExchangeRef(Exchange exchange, int session_id) {
        String exchangeFullName = exchange.toString();
        String refRoot = "/tme2/exchange/" + exchangeFullName;
        String exchangeRefPath = null;

        try {
            ZooKeeperInfo.Exchange.Builder builder = ZooKeeperInfo.Exchange.newBuilder();
            builder.setHost(exchange.getBroker());
            ZooKeeperInfo.Exchange xchgMsg = builder.build();

            ZNode refRootNode = new ZNode(refRoot);
            if(!refRootNode.exists())
                refRootNode.create(false, xchgMsg.toString().getBytes());
            else if(new String(refRootNode.getContent()).compareTo(xchgMsg.toString()) != 0)
                refRootNode.setContent(xchgMsg.toString().getBytes());

            ZNode refNode = genRefNode(refRoot);
            exchangeRefPath = refNode.getPath();
            synchronized(allExchangeRefs) {
                HashSet<ZNode> refSet = allExchangeRefs.get(exchangeFullName);
                if(refSet == null) {
                    refSet = new HashSet<ZNode>();
                    allExchangeRefs.put(exchangeFullName, refSet);
                }
                refSet.add(refNode);
                refNode.setNodeListener(new RefListener(refNode, exchange, refRoot));
            }
        }
        catch(Exception e) {
            logger.error("incExchangeRef(): " + e.toString());
        }
        return exchangeRefPath;
    }

    public void decExchangeRef(Exchange exchange) {
        String exchangeFullName = exchange.toString();
        String refRoot = "/tme2/exchange/" + exchangeFullName;

        HashSet<ZNode> refs = allExchangeRefs.get(exchangeFullName);
        for(;;) {
            ZNode refNode = null;
            synchronized(allExchangeRefs) {
                if(refs == null)
                    break;
                else if(refs.size() == 0)
                    break;
                else
                    refNode = refs.iterator().next();
            }
            try {
                if(!refNode.exists()) {
                    refs.remove(refNode);
                    continue;
                }
            }
            catch(CODIException e) {
                logger.warn(Utils.convertStackTrace(e));
            }
            try {
                refNode.delete();
                synchronized(allExchangeRefs) {
                    refs.remove(refNode);
                }
                break;
            }
            catch(Exception e) {
                logger.warn("decExchangeRef(): " + Utils.convertStackTrace(e) + " retry");
                continue;
            }
        }

        try {
            if(!hasReference(exchange)) {
                logger.info("decExchangeRef(): no reference, check exchange " + exchangeFullName);
                if(!hasPendingMessage(exchange)) {
                    logger.info("decExchangeRef(): no pending message, remove exchange");
                    new ZNode(refRoot).deleteRecursively();
                }
            }
        }
        catch(Exception e) {
            logger.error(e.getMessage());
        }
    }

    public String queryExchangeHost(Exchange exchange) {
        String realname = exchange.getName();

        String hostname = null;
        if((hostname = queryFixedExchange(realname)) != null)
            return hostname;

        String exchangeFullName = exchange.toString();
        String exchangeNodePath = "/tme2/exchange/" + exchangeFullName;

        ZNode exchangeNode = new ZNode(exchangeNodePath);
        try {
            if(!exchangeNode.exists())
                hostname = decideExchangeHost(realname);
            else {
                ZooKeeperInfo.Exchange.Builder exBuilder = ZooKeeperInfo.Exchange.newBuilder();
                TextFormat.merge(new String(exchangeNode.getContent()), exBuilder);
                ZooKeeperInfo.Exchange ex = exBuilder.build();
                hostname = ex.getHost();

                if(hostname.compareTo("") == 0) {
                    hostname = decideExchangeHost(realname);
                    exchangeNode.setContent(ZooKeeperInfo.Exchange.newBuilder().setHost(hostname).build().toString().getBytes());
                }
                else {
                    ZooKeeperInfo.Broker.Builder brkBuilder = ZooKeeperInfo.Broker.newBuilder();
                    ZNode brokerNode = new ZNode("/tme2/broker/" + hostname);
                    TextFormat.merge(new String(brokerNode.getContent()), brkBuilder);
                    if(brkBuilder.build().getStatus() != ZooKeeperInfo.Broker.Status.ONLINE) {
                        hostname = decideExchangeHost(realname);
                        ex = ZooKeeperInfo.Exchange.newBuilder().mergeFrom(ex).clearHost().setHost(hostname).build();
                        exchangeNode.setContent(ex.toString().getBytes());
                    }
                }
            }
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
        }
        return hostname;
    }

    public boolean belongsGOC(String exchange) {
        if(exchange.startsWith("queue:") || exchange.startsWith("topic:"))
            exchange = exchange.substring(6);
        return gocExchanges.contains(exchange);
    }

    public ZooKeeperInfo.TLSConfig belongsTLS(String exchange) {
        if(!tlsExchanges.containsKey(exchange))
            return null;
        return tlsExchanges.get(exchange);
    }

    public static class ExchangeInfo {
        public String name;
        public String host;
        public int refCount;
    }

    public static List<ExchangeInfo> getAllExchanges() {
        List<ExchangeInfo> list = new ArrayList<ExchangeInfo>();
        try {
            ZNode exchangeNode = new ZNode("/tme2/exchange");
            List<String> exchanges = exchangeNode.getChildren();
            for(String name : exchanges) {
                try {
                    ZNode exchangeChildNode = new ZNode("/tme2/exchange/" + name);
                    byte[] data = exchangeChildNode.getContent();
                    ZooKeeperInfo.Exchange.Builder ex_builder = ZooKeeperInfo.Exchange.newBuilder();
                    TextFormat.merge(new String(data), ex_builder);
                    ExchangeInfo info = new ExchangeInfo();
                    info.name = name;
                    info.host = ex_builder.build().getHost();
                    info.refCount = exchangeChildNode.getChildren().size();
                    list.add(info);
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return list;
    }
    
    public static String getCurrentExchangeHost(Exchange exchange) {
        String host = null;
        String exchangeFullName = exchange.toString();
        String exchangeNodePath = "/tme2/exchange/" + exchangeFullName;

        ZNode exchangeNode = new ZNode(exchangeNodePath);
        ZooKeeperInfo.Exchange.Builder exBuilder = ZooKeeperInfo.Exchange.newBuilder();
        try {
            TextFormat.merge(new String(exchangeNode.getContent()), exBuilder);
            ZooKeeperInfo.Exchange ex = exBuilder.build();
            host = ex.getHost();
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
        }
        return host;
    }

    public static BrokerAdmin.FlowControlBehavior getDropPolicy(Exchange exchange) {
        String path = "/tme2/global/drop_exchange" + "/" + exchange.getName();
        ZNode dropNode = new ZNode(path);
        try {
            if(dropNode.exists()) {
                ZooKeeperInfo.DropConfig.Builder dropBuilder = ZooKeeperInfo.DropConfig.newBuilder();
                TextFormat.merge(dropNode.getContentString(), dropBuilder);
                ZooKeeperInfo.DropConfig dropConf = dropBuilder.build();
                if(dropConf.getPolicy().equals(ZooKeeperInfo.DropConfig.Policy.NEWEST))
                    return BrokerAdmin.FlowControlBehavior.DROP_NEWEST;
                else
                    return BrokerAdmin.FlowControlBehavior.DROP_OLDEST;
            }
            else
                return BrokerAdmin.FlowControlBehavior.BLOCK;
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
            return BrokerAdmin.FlowControlBehavior.BLOCK;
        }
    }
    
    public static ZooKeeperInfo.TotalLimit getTotalLimit(Exchange exchange) {
        String path = "/tme2/global/limit_exchange" + "/" + exchange.getName();
        ZNode limitNode = new ZNode(path);
        try {
            ZooKeeperInfo.TotalLimit.Builder limitBuilder = ZooKeeperInfo.TotalLimit.newBuilder();
            TextFormat.merge(limitNode.getContentString(), limitBuilder);
            return limitBuilder.build();
        }
        catch(Exception e) {
            return ZooKeeperInfo.TotalLimit.newBuilder().setCount(100000).setSizeBytes(10485760).build();
        }
    }

    @Override
    public void onDataChanged(String parentPath, Map<String, byte[]> changeMap) {
        if(parentPath.compareTo(GOC_NODE) == 0)
            updateGocExchanges(changeMap);
        else if(parentPath.compareTo(TLS_NODE) == 0)
            updateTlsExchanges(changeMap);
        else if(parentPath.compareTo(FIXED_NODE) == 0)
            updateFixedExchanges(changeMap);
    }
}
