package com.trendmicro.tme.mfr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.TextFormat;
import com.trendmicro.codi.ZNode;
import com.trendmicro.mist.proto.ZooKeeperInfo;

public class ExchangeFarm {
    private final static Logger logger = LoggerFactory.getLogger(ExchangeFarm.class);

    private String tmeRoot;
    
    public static enum FlowControlBehavior {
        BLOCK, DROP_NEWEST, DROP_OLDEST,
    }
    
    public ExchangeFarm(String tmeRoot) {
        this.tmeRoot = tmeRoot;
    }

    public String getCurrentExchangeHost(Exchange exchange) {
        String host = null;
        String exchangeFullName = exchange.toString();
        String exchangeNodePath = tmeRoot + "/exchange/" + exchangeFullName;
        
        ZNode exchangeNode = new ZNode(exchangeNodePath);
        ZooKeeperInfo.Exchange.Builder exBuilder = ZooKeeperInfo.Exchange.newBuilder();
        try {
            TextFormat.merge(new String(exchangeNode.getContent()), exBuilder);
            ZooKeeperInfo.Exchange ex = exBuilder.build();
            host = ex.getHost();
        }
        catch(Exception e) {
            logger.error(e.getMessage(), e);
        }
        return host;
    }
    
    public FlowControlBehavior getDropPolicy(Exchange exchange) {
        String path = tmeRoot + "/global/drop_exchange" + "/" + exchange.getName();
        ZNode dropNode = new ZNode(path);
        try {
            if(dropNode.exists()) {
                ZooKeeperInfo.DropConfig.Builder dropBuilder = ZooKeeperInfo.DropConfig.newBuilder();
                TextFormat.merge(dropNode.getContentString(), dropBuilder);
                ZooKeeperInfo.DropConfig dropConf = dropBuilder.build();
                if(dropConf.getPolicy().equals(ZooKeeperInfo.DropConfig.Policy.NEWEST))
                    return FlowControlBehavior.DROP_NEWEST;
                else
                    return FlowControlBehavior.DROP_OLDEST;
            }
            else
                return FlowControlBehavior.BLOCK;
        }
        catch(Exception e) {
            logger.error(e.getMessage(), e);
            return FlowControlBehavior.BLOCK;
        }
    }
    
    public ZooKeeperInfo.TotalLimit getTotalLimit(Exchange exchange) {
        String path = tmeRoot + "/global/limit_exchange" + "/" + exchange.getName();
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
}
