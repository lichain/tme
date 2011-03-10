
package com.trendmicro.mist;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.remote.JMXConnector;
import com.sun.messaging.AdminConnectionConfiguration;
import com.sun.messaging.AdminConnectionFactory;
import com.trendmicro.mist.proto.ZooKeeperInfo;
import com.trendmicro.mist.util.Address;
import com.trendmicro.mist.util.Credential;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.spn.common.util.Utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BrokerSpy {
    private static Log logger = LogFactory.getLog(BrokerSpy.class);
    private Address jmxAddress = new Address();
    private Credential jmxAuth = new Credential();
    private String brokerType;
    private String brokerHost;
    private String brokerPort;
    private String brokerVersion;

    private JMXConnector connector;
    private MBeanServerConnection connection;

    private void connectBrokerJMX() throws Exception {
        for(int i = 0; i < 10; i++) {
            try {
                jmxConnectServer();
                if(brokerType.equals("openmq")) {
                    ObjectName objname = new ObjectName("com.sun.messaging.jms.server:type=Broker,subtype=Config");
                    List<Attribute> attrList = connection.getAttributes(objname, new String[] {
                        "Port", "Version"
                    }).asList();
                    brokerHost = jmxAddress.getHost();
                    brokerPort = ((Integer) (attrList.get(0).getValue())).toString();
                    brokerVersion = (String) (attrList.get(1).getValue());
                    jmxCloseServer();
                    break;
                }
                else {
                }
            }
            catch(Exception e) {
                Utils.justSleep(1000);
            }
            finally {
                jmxCloseServer();
                if(i == 9)
                    throw new Exception("cannot connect to broker");
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    
	public BrokerSpy(String type, String jmxserver, String jmxauth) throws Exception {
	    brokerType = type;
	    jmxAddress.set(jmxserver);
	    jmxAuth.set(jmxauth);
		connectBrokerJMX();
	}
	
	public ZooKeeperInfo.Broker getBroker() throws MistException{
        try {
            jmxConnectServer();
            if (brokerType.equals("openmq")) {
                ZooKeeperInfo.Broker.Builder broker_builder = ZooKeeperInfo.Broker.newBuilder();
                broker_builder.setHost(getBrokerHost());
                broker_builder.setPort(getBrokerPort());
                broker_builder.setBrokerType(getBrokerType());
                broker_builder.setVersion(getBrokerVersion());
                broker_builder.setStatus(ZooKeeperInfo.Broker.Status.ONLINE);
                broker_builder.setReserved(false);
                return broker_builder.build();
            }
            else {
                throw new MistException("not implemented");
            }
        }
        catch (Exception e) {
            throw new MistException(e.getMessage());
        }
        finally {
            jmxCloseServer();
        }       
    }
	
    public String getExchangeAttribs(boolean isQueue, String exchangeName, String attrib) {
        String pattern = String.format("com.sun.messaging.jms.server:type=Destination,subtype=Monitor,desttype=%s,name=\"%s\"", isQueue ? "q": "t", exchangeName);
        try {
            jmxConnectServer();
            Map<String, String> map = getMBeanAttributesMap(pattern, null);
            return map.get(attrib);
        }
        catch(Exception e) {
            logger.error(e.getMessage());
        }
        finally {
            jmxCloseServer();
        }
        return null;
    }
	
	public ZooKeeperInfo.Loading doSpy() throws MistException {
		try {
            jmxConnectServer();
            if(brokerType.equals("openmq")) {
                ObjectName objname = new ObjectName("com.sun.messaging.jms.server:type=JVM,subtype=Monitor");
                List<Attribute> attrList = connection.getAttributes(objname, new String[] { "FreeMemory", "MaxMemory" }).asList();
                long free = (Long) (attrList.get(0).getValue());
                long max = (Long) (attrList.get(1).getValue());
                ZooKeeperInfo.Loading.Builder load_builder = ZooKeeperInfo.Loading.newBuilder();
                load_builder.setLoading(Math.round(((float) (max - free) / max) * 100));
                load_builder.setLastUpdate(new Date().getTime());
                load_builder.setFreeMemory(free);
                load_builder.setMaxMemory(max);
                return load_builder.build();
            }
            else {
                throw new MistException("not implemented");
            }
		} 
		catch(Exception e) {
		    throw new MistException(e.getMessage());
		}
		finally {
            jmxCloseServer();
		}
	}
	
    public void jmxConnectServer() throws Exception {
        Exception e = null;
        for(int i = 0; i <= 60; i++) {
            try {
                AdminConnectionFactory acf;
                acf = new AdminConnectionFactory();
                acf.setProperty(AdminConnectionConfiguration.imqAddress, jmxAddress.getHost());
                connector = acf.createConnection();
                connection = connector.getMBeanServerConnection();
                return;
            }
            catch(Exception e2) {
                logger.warn("jmx connect error: retry #" + i);
                e = e2;
                Utils.justSleep(1000);
            }
        }
        logger.error("jmxConnectServer gave up retrying");
        throw e;
    }

    public void jmxCloseServer() {
        try {
            if(connector != null)
                connector.close();
        }
        catch(IOException e) {
            logger.error(e.getMessage());
        }
    }

	public String[] getMBeanAttributeNames(ObjectName name) {
        try {
            MBeanInfo info = connection.getMBeanInfo(name);
            MBeanAttributeInfo[] attrinfo = info.getAttributes();
            String[] attrnames = new String[attrinfo.length];
            int i = 0;
            for(MBeanAttributeInfo a : attrinfo)
                attrnames[i++] = a.getName();
            return attrnames;
        }
        catch(Exception e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    public Map<String, AttributeList> getMBeanAttributes(String name, QueryExp query) {
        try {
            Set<ObjectName> names = connection.queryNames(new ObjectName(name), query);
            HashMap<String, AttributeList> map = new HashMap<String, AttributeList>();
            for(ObjectName n : names) {
                String objname = n.getCanonicalName();
                String[] attrnames = getMBeanAttributeNames(n);
                AttributeList attrs = connection.getAttributes(n, attrnames);
                map.put(objname, attrs);
            }
            return map;
        }
        catch(Exception e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    public Map<String, String> getMBeanAttributesMap(String name, QueryExp query) {
        Map<String, AttributeList> map = getMBeanAttributes(name, query);
        Map<String, String> result = new HashMap<String, String>();
        Set<String> keys = map.keySet();
        for(String k : keys) {
            List<Attribute> attrs = map.get(k).asList();
            for(Attribute a : attrs)
                result.put(a.getName(), (a.getValue() == null) ? "": a.getValue().toString());
        }
        return result;
    }

    public String getBrokerType() {
        return brokerType;
    }

    public String getBrokerHost() {
        return brokerHost;
    }

    public String getBrokerPort() {
        return brokerPort;
    }

    public String getBrokerVersion() {
        return brokerVersion;
    }
    
    public ArrayList<Exchange> getAllExchangeMetadata() throws Exception{        
        try {
            jmxConnectServer();
            ArrayList<Exchange> result = new ArrayList<Exchange>();

            if(brokerType.equals("openmq")) {
                ObjectName objname = new ObjectName("com.sun.messaging.jms.server:type=Destination,subtype=Monitor,desttype=q,name=*");
                for(ObjectName n : connection.queryNames(objname, null)) {
                    Exchange excQ = new Exchange(connection.getAttribute(n, "Name").toString());
                    excQ.setQueue();
                    result.add(excQ);
                }
                objname = new ObjectName("com.sun.messaging.jms.server:type=Destination,subtype=Monitor,desttype=t,name=*");
                for(ObjectName n : connection.queryNames(objname, null)){
                    Exchange excT = new Exchange(connection.getAttribute(n, "Name").toString());
                    excT.setTopic();
                    result.add(excT);
                }
                Exchange excAdmin = new Exchange("mq.sys.dmq");
                excAdmin.setQueue();                
                result.remove(excAdmin);
                return result;
            }
            else {
                throw new MistException("not implemented");
            }
        } 
        catch(Exception e) {
            throw new MistException(e.getMessage());
        }
        finally {
            jmxCloseServer();
        }        
    }
    
    public Object invokeMBeanMethod(String name, String op_name, Object[] params) throws MistException {
        Object result = null;
        try {
            jmxConnectServer();
            Set<ObjectName> names = connection.queryNames(new ObjectName(name), null);
            for(ObjectName n : names) {
                result = connection.invoke(n, op_name, params, null);
                break;
            }            
        }catch(Exception e) {
            throw new MistException(e.getMessage());
        }
        finally {
            jmxCloseServer();
        }     

        return result;
    }
}
