
package com.trendmicro.mist;

import java.util.concurrent.TimeoutException;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.trendmicro.mist.mfr.BrokerFarm;
import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.session.Session;
import com.trendmicro.mist.session.SessionPool;
import com.trendmicro.mist.util.ConnectionList;
import com.trendmicro.spn.common.FixedReconnect;
import com.trendmicro.spn.common.InfiniteReconnect;
import com.trendmicro.spn.common.ReconnectCounter;
import com.trendmicro.spn.common.util.Utils;

public class Connection implements ExceptionListener 
{
    private GateTalk.Connection connectionConfig;
    private javax.jms.Connection connection = null;
    private ConnectionList connList = new ConnectionList();
    private int myId = -1;
    private static int connectionIdCnt = 0;
    private static Log logger = LogFactory.getLog(Connection.class);
    private boolean connected = false;
    private int referenceCount = 0;
    private boolean isOpenMQCluster = false;

    private void createJMSConnection() throws MistException {
        try {
            tryConnect(new FixedReconnect(3, 150));
        }
        catch(MistException e) {
            throw e;
        }
    }

    private void closeJMSConnection() {
        if(connected) {
            try { 
                connection.close();
                logger.info(String.format("%d: `%s' closed", getId(), getConnectionString()));
            }
            catch(JMSException e) {
                logger.error("can not close connection");
            }
            connected = false;
            connection = null;
        }
    }

    private void tryConnect(ReconnectCounter counter) throws MistException { 
        if(connected) 
            closeJMSConnection();

        counter.init();
        do {
            connected = false;
            try {
                connection = BrokerFarm.prepareJMSConnection(connectionConfig.getBrokerType(), connList, connectionConfig.getUsername(), connectionConfig.getPassword());
                connection.setExceptionListener(this);
                connection.start();
                connected = true;                
                logger.info(String.format("%d: `%s' connected. Current connected broker: %s", getId(), getConnectionString(), connection.toString()));
            }
            catch(JMSException e) {
                logger.error(String.format("%d: %s", getId(), e.getMessage()));
                if(Daemon.isShutdownRequested()) { 
                    logger.warn("shutdown, abort re-connection");
                    break;
                }
                try {
                    counter.waitAndCheckCounter();
                    if(referenceCount <= 0) {
                        Daemon.connectionPool.remove(this);
                        break;
                    }
                }
                catch(TimeoutException e2) {
                    throw new MistException(String.format("reach re-connect limit %d, abort", counter.getCounter()));
                }
            }
        } while(connected == false);
    }

    private synchronized void reconnect() {
        logger.warn(String.format("Re-connecting (%d)", myId));
        try {
            tryConnect(new InfiniteReconnect());
        }
        catch(Exception e) {
            logger.fatal(Utils.convertStackTrace(e));
            logger.fatal("connection %d: fail to reconnect");
            return;
        }
        for(Session sess : SessionPool.pool.values()) {
            if(sess.isAttached()) {
                for(Client client : sess.getClientList()) {
                    if(client.getConnection() == this) {
                        sess.migrateClient(client.getExchange());
                    }
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public Connection(GateTalk.Connection conn_config) {
        connectionConfig = conn_config;
        connList.set(connectionConfig.getHostName(), connectionConfig.getHostPort());
        if(connectionConfig.getBrokerType().equals("openmq") && connList.size() > 1)
            isOpenMQCluster = true;
        myId = connectionIdCnt++;
    }

    public GateTalk.Connection getConfig() {
        return connectionConfig;
    }

    public String getType() {
        return connectionConfig.getBrokerType();
    }

    public javax.jms.Connection getJMSConnection() {
        return connection;
    }

    public void open()  throws MistException {
        try {
            createJMSConnection();
        }
        catch(MistException e) {
            throw e;
        }
    }

    public void close() {
        closeJMSConnection();
    }
    
    public int getId() {
        return myId;
    }

    public boolean isConnected() {
        return connected;
    }
    
    public String getHostName() {
        return connectionConfig.getHostName();
    }
    
    public String getActiveBroker() {       
        if (connectionConfig.getBrokerType().equals("openmq"))            
            return ((com.sun.messaging.jms.Connection) connection).getBrokerAddress();
        else
            return "";
    }

    public String getConnectionString() {
        if(connList.size() > 1) 
            return String.format("%s (active: %s)", connList.toString(), getActiveBroker());
        return connList.toString();
    }
    
    public boolean isOpenMQCluster() {
        return isOpenMQCluster;
    }

    public void onException(JMSException e) {
        logger.error(String.format("connection %d: received JMSException; stacktrace: %s", getId(), Utils.convertStackTrace(e)));        
        reconnect();
    }

    public void increaseReference() {
        synchronized(Daemon.connectionPool) {
            referenceCount++;
        }
    }
    
    public void decreaseReference() {
        synchronized(Daemon.connectionPool) {
            referenceCount--;
            if(referenceCount == 0){
                close();
                Daemon.connectionPool.remove(this);
            }
        }
    }
    
    public int getReferenceCount() {
        return referenceCount;
    }
}
