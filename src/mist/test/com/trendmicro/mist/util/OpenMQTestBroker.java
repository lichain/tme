package com.trendmicro.mist.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.Map.Entry;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import com.sun.messaging.ConnectionConfiguration;
import com.sun.messaging.jmq.jmsclient.runtime.BrokerInstance;
import com.sun.messaging.jmq.jmsclient.runtime.ClientRuntime;
import com.sun.messaging.jmq.jmsservice.JMSServiceException;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZNode;
import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.proto.ZooKeeperInfo;
import com.trendmicro.mist.session.ProducerSession;

public class OpenMQTestBroker {
    private static final String MQ_VAR_BASE = "/tmp/testMqVar/";
    private static final String MQ_HOME_BASE = "/tmp/testMqHome/";

    private BrokerInstance brokerInstance;
    private Connection connection;
    private Session session;
    private int port;
    private String name;

    private void envSetup() throws InterruptedException, IOException {
        Runtime.getRuntime().exec("mkdir -p " + MQ_HOME_BASE + name + "/lib/props/broker").waitFor();
        Runtime.getRuntime().exec("touch " + MQ_HOME_BASE + name + "/lib/props/broker/default.properties").waitFor();
    }

    private void envCleanUp() {
        try {
            Runtime.getRuntime().exec("rm -rf " + MQ_VAR_BASE + name).waitFor();
            Runtime.getRuntime().exec("rm -rf " + MQ_HOME_BASE + name).waitFor();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    public OpenMQTestBroker(String name, Integer port) throws ClassNotFoundException, IllegalAccessException, InstantiationException, InterruptedException, IOException {
        this.name = name;
        this.port = port;
        envCleanUp();
        envSetup();

        ClientRuntime clientRuntime = ClientRuntime.getRuntime();
        brokerInstance = clientRuntime.createBrokerInstance();

        Properties props = brokerInstance.parseArgs(new String[] {
            "-imqhome", MQ_HOME_BASE + name, "-varhome", MQ_VAR_BASE + name, "-name", name, "-port", port.toString()
        });

        brokerInstance.init(props, null);
    }

    public void sendMessage(boolean isQueue, String name, byte[] content) throws JMSException {
        Destination dest;
        if(isQueue)
            dest = session.createQueue(name);
        else
            dest = session.createTopic(name);
        MessageProducer producer = session.createProducer(dest);
        BytesMessage msg = session.createBytesMessage();
        msg.writeBytes(content);
        producer.send(msg);
        producer.close();
    }

    public void sendMessage(boolean isQueue, String name, byte[] content, long ttl, HashMap<String, String> props) throws JMSException {
        Destination dest;
        if(isQueue)
            dest = session.createQueue(name);
        else
            dest = session.createTopic(name);
        MessageProducer producer = session.createProducer(dest);
        BytesMessage msg = session.createBytesMessage();
        msg.writeBytes(content);
        for(Entry<String, String> ent : props.entrySet())
            msg.setStringProperty(ent.getKey(), ent.getValue());
        msg.setStringProperty(ProducerSession.MIST_MESSAGE_TTL, new Long(ttl).toString());
        producer.setTimeToLive(ttl);
        producer.send(msg);
        producer.close();
    }

    public BytesMessage getBytesMessage(boolean isQueue, String name) throws JMSServiceException, JMSException {
        Destination dest;
        if(isQueue)
            dest = session.createQueue(name);
        else
            dest = session.createTopic(name);
        MessageConsumer consumer = session.createConsumer(dest);
        Message msg = consumer.receive(3000);
        consumer.close();
        if(msg == null)
            return null;
        else
            return (BytesMessage) msg;
    }

    public byte[] getMessage(boolean isQueue, String name) throws JMSServiceException, JMSException {
        Destination dest;
        if(isQueue)
            dest = session.createQueue(name);
        else
            dest = session.createTopic(name);
        MessageConsumer consumer = session.createConsumer(dest);
        Message msg = consumer.receive(3000);
        if(msg == null)
            return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if(msg instanceof BytesMessage) {
            byte[] block = new byte[256];
            int ret = -1;
            while((ret = ((BytesMessage) msg).readBytes(block)) > 0)
                bos.write(block, 0, ret);
        }
        consumer.close();
        return bos.toByteArray();
    }

    public void start() throws JMSException {
        Daemon.connectionPool.clear();
        brokerInstance.start();
        com.sun.messaging.ConnectionFactory cf = new com.sun.messaging.ConnectionFactory();
        cf.setProperty(ConnectionConfiguration.imqAddressList, "localhost:" + port);
        connection = cf.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    public void stop() {
        try {
            connection.stop();
            connection.close();
        }
        catch(Exception e) {
        }
        brokerInstance.stop();
        brokerInstance.shutdown();
        envCleanUp();
    }

    public void registerOnZk() throws CODIException {
        ZooKeeperInfo.Broker.Builder brkBuilder = ZooKeeperInfo.Broker.newBuilder();
        brkBuilder.setHost("127.0.0.1");
        brkBuilder.setPort(new Integer(port).toString());
        brkBuilder.setStatus(ZooKeeperInfo.Broker.Status.ONLINE);
        brkBuilder.addAccount(ZooKeeperInfo.Broker.Account.newBuilder().setUser("admin").setPassword("admin").build());
        brkBuilder.setBrokerType("openmq");
        brkBuilder.setVersion("4.4");
        brkBuilder.setReserved(false);

        ZNode brkNode = new ZNode("/tme2/broker/127.0.0.1");
        brkNode.create(false, brkBuilder.build().toString());

        ZNode loadingNode = new ZNode("/tme2/broker/127.0.0.1/loading");
        loadingNode.create(false, ZooKeeperInfo.Loading.newBuilder().setLoading(0).setLastUpdate(0).setFreeMemory(0).setMaxMemory(0).build().toString());
    }
}
