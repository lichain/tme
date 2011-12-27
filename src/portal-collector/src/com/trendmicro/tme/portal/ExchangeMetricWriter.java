package com.trendmicro.tme.portal;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.jmxtrans.model.Query;
import com.googlecode.jmxtrans.model.Result;
import com.googlecode.jmxtrans.model.output.RRDToolWriter;
import com.googlecode.jmxtrans.util.BaseOutputWriter;
import com.googlecode.jmxtrans.util.LifecycleException;
import com.googlecode.jmxtrans.util.ValidationException;
import com.sun.messaging.AdminConnectionConfiguration;
import com.sun.messaging.AdminConnectionFactory;

public class ExchangeMetricWriter extends BaseOutputWriter {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeMetricWriter.class);
    private static final String[] MBEAN_INVOKE_SIG = new String[] {
        String.class.getName()
    };
    private static final ObjectName consumerManagerName;
    private static final ObjectName producerManagerName;
    
    private String templateFile = "";
    private String outputPath = "";
    private Pattern namePattern = Pattern.compile(".*,name=\"([^\"]*)\",.*");
    private Pattern typePattern = Pattern.compile(".*desttype=(.),.*");
    private ObjectMapper mapper = new ObjectMapper();
    
    private Map<String, RRDToolWriter> writerMap = new HashMap<String, RRDToolWriter>();
    
    static {
        try {
            consumerManagerName = new ObjectName("com.sun.messaging.jms.server:type=ConsumerManager,subtype=Monitor");
            producerManagerName = new ObjectName("com.sun.messaging.jms.server:type=ProducerManager,subtype=Monitor");
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private RRDToolWriter getWriter(String broker, String exchangeName, boolean isQueue) throws LifecycleException {
        String key = broker + exchangeName + (isQueue ? "queue": "topic");
        if(!writerMap.containsKey(key)) {
            RRDToolWriter writer = new RRDToolWriter();
            writer.addSetting(RRDToolWriter.TEMPLATE_FILE, templateFile);
            writer.addSetting(RRDToolWriter.OUTPUT_FILE, String.format("%s/%s-%s.rrd", outputPath, isQueue ? "queue": "topic", exchangeName));
            writer.addSetting(RRDToolWriter.BINARY_PATH, "/usr/bin");
            writer.addSetting(RRDToolWriter.DEBUG, true);
            writer.addSetting(RRDToolWriter.GENERATE, false);
            writerMap.put(key, writer);
        }
        return writerMap.get(key);
    }
    
    private void queryClients(String broker, String typeName, ExchangeMetric metric) throws Exception {
        JMXConnector connector;
        MBeanServerConnection connection;
        AdminConnectionFactory acf;
        acf = new AdminConnectionFactory();
        acf.setProperty(AdminConnectionConfiguration.imqAddress, broker);
        connector = acf.createConnection();
        
        try {
            connection = connector.getMBeanServerConnection();
            ObjectName exchangeManagerName = new ObjectName("com.sun.messaging.jms.server:" + typeName);
            String[] consumerIDs = (String[]) connection.invoke(exchangeManagerName, "getConsumerIDs", null, null);
            if(consumerIDs != null) {
                for(String consumerID : consumerIDs) {
                    CompositeData info = (CompositeData) connection.invoke(consumerManagerName, "getConsumerInfoByID", new Object[] {
                        consumerID
                    }, MBEAN_INVOKE_SIG);
                    metric.addConsumer(info.get("Host").toString());
                }
            }
            
            String[] producerIDs = (String[]) connection.invoke(exchangeManagerName, "getProducerIDs", null, null);
            if(producerIDs != null) {
                for(String producerID : producerIDs) {
                    CompositeData info = (CompositeData) connection.invoke(producerManagerName, "getProducerInfoByID", new Object[] {
                        producerID
                    }, MBEAN_INVOKE_SIG);
                    metric.addProducer(info.get("Host").toString());
                }
            }
        }
        finally {
            connector.close();
        }
    }
    
    @Override
    public void doWrite(Query q) throws Exception {
        if(q.getResults().isEmpty()) {
            logger.error("Empty query result!");
            return;
        }
        
        Matcher m = namePattern.matcher(q.getResults().get(0).getTypeName());
        if(!m.matches()) {
            logger.error("Name parsing error: {}", q.getResults().get(0).getTypeName());
            return;
        }
        String exchangeName = m.group(1);
        
        m = typePattern.matcher(q.getResults().get(0).getTypeName());
        if(!m.matches()) {
            logger.error("Type parsing error: {}", q.getResults().get(0).getTypeName());
            return;
        }
        boolean isQueue = m.group(1).equals("q");
        
        if(exchangeName.equals("mq.sys.dmq")) {
            return;
        }
        
        RRDToolWriter writer = getWriter(q.getServer().getHost(), exchangeName, isQueue);
        ExchangeMetric metric = new ExchangeMetric(q.getServer().getHost(), isQueue ? "queue": "topic", exchangeName, String.format("%s/%s-%s.rrd", outputPath, isQueue ? "queue": "topic", exchangeName));
        
        queryClients(q.getServer().getHost(), q.getResults().get(0).getTypeName(), metric);
        
        long numMsgs = 0;
        long numMsgsIn = 0;
        long numMsgsOut = 0;
        for(Result res : q.getResults()) {
            if(res.getAttributeName().equals("NumMsgs")) {
                numMsgs = Long.valueOf((String) res.getValues().get("NumMsgs"));
                metric.addMetric("Pending", res.getValues().get("NumMsgs").toString());
            }
            else if(res.getAttributeName().equals("NumMsgsIn")) {
                numMsgsIn = Long.valueOf((String) res.getValues().get("NumMsgsIn"));
                metric.addMetric("Enqueue", res.getValues().get("NumMsgsIn").toString());
            }
            else if(res.getAttributeName().equals("NumMsgsOut")) {
                numMsgsOut = Long.valueOf((String) res.getValues().get("NumMsgsOut"));
                metric.addMetric("Dequeue", res.getValues().get("NumMsgsOut").toString());
            }
            else if(res.getAttributeName().equals("NumMsgsPendingAcks")) {
                metric.addMetric("Pending ACK", res.getValues().get("NumMsgsPendingAcks").toString());
            }
            else if(res.getAttributeName().equals("NumConsumers")) {
                metric.addMetric("Consumers", res.getValues().get("NumConsumers").toString());
            }
            else if(res.getAttributeName().equals("NumProducers")) {
                metric.addMetric("Producers", res.getValues().get("NumProducers").toString());
            }
            else if(res.getAttributeName().equals("MsgBytesIn")) {
                metric.addMetric("Enqueue Size", res.getValues().get("MsgBytesIn").toString());
            }
            else if(res.getAttributeName().equals("MsgBytesOut")) {
                metric.addMetric("Dequeue Size", res.getValues().get("MsgBytesOut").toString());
            }
            else if(res.getAttributeName().equals("TotalMsgBytes")) {
                metric.addMetric("Pending Size", res.getValues().get("TotalMsgBytes").toString());
            }
        }
        long numMsgsDropped = numMsgsIn - numMsgsOut - numMsgs;
        q.getResults().get(0).addValue("NumMsgDropped", String.valueOf(numMsgsDropped));
        metric.addMetric("Dropped", String.valueOf(numMsgsDropped));
        
        writer.validateSetup(q);
        writer.doWrite(q);
        
        File file = new File(String.format("%s/%s-%s.json", outputPath, isQueue ? "queue": "topic", exchangeName));
        FileChannel channel = new RandomAccessFile(file, "rw").getChannel();
        try {
            FileLock lock = channel.lock();
            try {
                mapper.writeValue(file, metric);
            }
            catch(Exception e) {
                logger.error("write metric error: ", e);
            }
            lock.release();
        }
        catch(Exception e) {
            logger.error("Cannot lock file {}", file.getAbsolutePath());
        }
        finally {
            channel.close();
        }
    }
    
    @Override
    public void validateSetup(Query q) throws ValidationException {
        try {
            templateFile = (String) getSettings().get("templateFile");
            outputPath = (String) getSettings().get("outputPath");
        }
        catch(Exception e) {
            throw new ValidationException(e.getMessage(), q);
        }
    }
}
