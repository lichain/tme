package com.trendmicro.mist;

import java.io.IOException;

import javax.jms.JMSException;
import javax.management.Attribute;
import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sun.messaging.AdminConnectionConfiguration;
import com.sun.messaging.AdminConnectionFactory;
import com.trendmicro.mist.mfr.ExchangeFarm;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.spn.common.util.Utils;

public class BrokerAdmin {
    private static Log logger = LogFactory.getLog(BrokerAdmin.class);

    public static enum FlowControlBehavior {
        BLOCK, DROP_NEWEST, DROP_OLDEST,
    }

    private static JMXConnector createJMXConnector(String host) throws JMSException, IOException, JMException {
        AdminConnectionFactory acf;

        acf = new AdminConnectionFactory();
        acf.setProperty(AdminConnectionConfiguration.imqAddress, host);

        JMXConnector jmxc = acf.createConnection();
        return jmxc;
    }

    public static void setExchangeFlowControl(Exchange exchange, FlowControlBehavior policy) {
        JMXConnector jmxconn = null;
        try {
            jmxconn = createJMXConnector(ExchangeFarm.getCurrentExchangeHost(exchange));
            MBeanServerConnection conn = jmxconn.getMBeanServerConnection();
            String pattern = String.format("com.sun.messaging.jms.server:type=Destination,subtype=Config,desttype=q,name=\"%s\"", exchange.getName());
            ObjectName name = conn.queryNames(new ObjectName(pattern), null).iterator().next();

            Attribute attr = null;
            switch(policy) {
            case BLOCK:
                attr = new Attribute("LimitBehavior", "FLOW_CONTROL");
                break;
            case DROP_NEWEST:
                attr = new Attribute("LimitBehavior", "REJECT_NEWEST");
                break;
            case DROP_OLDEST:
                attr = new Attribute("LimitBehavior", "REMOVE_OLDEST");
                break;
            }
            conn.setAttribute(name, attr);
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
        }
        finally {
            try {
                jmxconn.close();
            }
            catch(Exception e) {
            }
        }
    }

    public static void setExchangeTotalLimit(Exchange exchange, long sizeBytes, long count) {
        JMXConnector jmxconn = null;
        try {
            jmxconn = createJMXConnector(ExchangeFarm.getCurrentExchangeHost(exchange));
            MBeanServerConnection conn = jmxconn.getMBeanServerConnection();
            String pattern = String.format("com.sun.messaging.jms.server:type=Destination,subtype=Config,desttype=q,name=\"%s\"", exchange.getName());
            ObjectName name = conn.queryNames(new ObjectName(pattern), null).iterator().next();

            Attribute attr = new Attribute("MaxTotalMsgBytes", sizeBytes);
            conn.setAttribute(name, attr);
            attr = new Attribute("MaxNumMsgs", count);
            conn.setAttribute(name, attr);
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
        }
        finally {
            try {
                jmxconn.close();
            }
            catch(Exception e) {
            }
        }
    }
}
