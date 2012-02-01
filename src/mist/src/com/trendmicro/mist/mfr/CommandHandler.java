package com.trendmicro.mist.mfr;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.TextFormat;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.DataListener;
import com.trendmicro.codi.DataObserver;
import com.trendmicro.codi.ZNode;
import com.trendmicro.mist.proto.ZooKeeperInfo;
import com.trendmicro.mist.session.ConsumerSession;
import com.trendmicro.mist.session.ProducerSession;
import com.trendmicro.mist.session.Session;
import com.trendmicro.mist.session.SessionPool;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.spn.common.util.Utils;

public class CommandHandler extends Thread implements DataListener {
    private final static Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    private static CommandHandler m_theSingleton = null;
    private static final String NODE_PATH = "/local/mist_client/" + Utils.getHostIP();
    private ZNode commandNode = null;
    private DataObserver obs = null;
    private LinkedBlockingDeque<ZooKeeperInfo.Command> cmdQueue = new LinkedBlockingDeque<ZooKeeperInfo.Command>();

    public static CommandHandler getInstance() {
        if(null == m_theSingleton)
            m_theSingleton = new CommandHandler();
        return m_theSingleton;
    }

    private void migrateExchange(Exchange exchange) {
        for(Session sess : SessionPool.pool.values()) {
            if(sess instanceof ConsumerSession) {
                sess.migrateClient(exchange);
            }
        }
        for(Session sess : SessionPool.pool.values()) {
            if(sess instanceof ProducerSession) {
                sess.migrateClient(exchange);
            }
        }
    }

    private CommandHandler() {
        commandNode = new ZNode(NODE_PATH);
        try {
            commandNode.deleteRecursively();
        }
        catch(CODIException e) {
        }
        try {
            commandNode.create(false, "".getBytes());
        }
        catch(CODIException e) {
            logger.error("cannot create command node!");
        }
        obs = new DataObserver(NODE_PATH, this, true, 1000);
        obs.start();
        new Thread(this).start();
    }

    public void run() {
        Thread.currentThread().setName("CommandHandler");
        for(;;) {
            ZooKeeperInfo.Command cmd;
            try {
                cmd = cmdQueue.take();
            }
            catch(InterruptedException e) {
                continue;
            }

            switch(cmd.getType()) {
            case MIGRATE_EXCHANGE:
                logger.info("migrating exchange " + cmd.getArgument(0));
                Exchange exchange = new Exchange(cmd.getArgument(0));
                migrateExchange(exchange);
                break;
            default:
                break;
            }
        }
    }

    @Override
    public void onDataChanged(String parentPath, Map<String, byte[]> changeMap) {
        for(Entry<String, byte[]> ent : changeMap.entrySet()) {
            if(ent.getKey().length() == 0)
                continue;
            else if(ent.getValue() == null)
                continue;

            ZooKeeperInfo.Command.Builder cmdBuilder = ZooKeeperInfo.Command.newBuilder();
            try {
                TextFormat.merge(new String(ent.getValue()), cmdBuilder);
                cmdQueue.put(cmdBuilder.build());
            }
            catch(Exception e) {
                logger.error("unable to process command " + new String(ent.getValue()));
            }
            finally {
                try {
                    new ZNode(parentPath + "/" + ent.getKey()).delete();
                }
                catch(CODIException e) {
                    logger.error("cannot delete command node " + parentPath + ent.getKey());
                }
            }
        }
    }
}
