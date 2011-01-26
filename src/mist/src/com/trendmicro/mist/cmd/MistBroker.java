package com.trendmicro.mist.cmd;

import java.util.Map;
import java.util.Set;
import java.util.List;
import javax.management.QueryExp;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeValueExp;
import javax.management.StringValueExp;
import javax.management.Query;

import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.BrokerSpy;
import com.trendmicro.mist.util.Address;
import com.trendmicro.mist.util.Credential;
import com.trendmicro.spn.common.util.Utils;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

public class MistBroker {
    enum CmdType {
        ALL, BROKER, QUEUE, TOPIC, HOST, HELP,
    }

    private CmdType currCmd = CmdType.ALL;
    private String brokerType;
    private Address jmxAddress = new Address();
    private Credential jmxAuth = new Credential();
    private String destinationName = "*";
    private static MistBroker myApp;
    private int retValue = 0;
    private BrokerSpy brokerSpy;

    private void printUsage() {
        System.out.printf("Usage:%n");
        System.out.printf("      mist-broker [options [arguments...] ]... %n%n");
        System.out.printf("Options: %n");
        System.out.printf("  --host=HOST:PORT, -b HOST:PORT %n");
        System.out.printf("        broker host to connect, default %s %n%n", jmxAddress.toString());
        System.out.printf("    --auth=USERNAME:PASSWORD, -u USERNAME:PASSWORD %n");
        System.out.printf("        specify account for authentication %n%n");
        System.out.printf("    --type=[activemq|openmq], -y [activemq|openmq] %n");
        System.out.printf("        specify broker type (default: openmq) %n%n");
        System.out.printf("  --broker, -i %n");
        System.out.printf("        show broker information %n%n");
        System.out.printf("  --all[=EXCHANGENAME] %n");
        System.out.printf("        show all destinations (default), specify EXCHANGENAME to filter result %n%n");
        System.out.printf("  --queue[=EXCHANGENAME] %n");
        System.out.printf("  --topic[=EXCHANGENAME] %n");
        System.out.printf("        disply queue or topic only %n%n");
        System.out.printf("  --help, -h %n");
        System.out.printf("        display help messages %n%n");
    }

    private void listMBeanAttributes(String name, QueryExp query) {
        Map<String, AttributeList> map = brokerSpy.getMBeanAttributes(name, query);
        Set<String> keys = map.keySet();
        for(String k : keys) {
            System.out.printf("==> %s%n", k);
            List<Attribute> attrs = map.get(k).asList();
            for(Attribute a : attrs) {
                System.out.printf("%-40s: %s%n", a.getName(), a.getValue());
            }
        }
    }

    private void listBrokerInfo() {
        String pattern = String.format("org.apache.activemq:BrokerName=*,Type=Broker");
        if(brokerType.equals("openmq"))
            pattern = String.format("com.sun.messaging.jms.server:type=Broker,subtype=Config");
        listMBeanAttributes(pattern, null);
    }

    private void listDestination(String type) {
        String pattern = String.format("org.apache.activemq:BrokerName=*,Type=%s,Destination=%s", type, destinationName);
        if(brokerType.equals("openmq"))
            pattern = String.format("com.sun.messaging.jms.server:type=Destination,subtype=Monitor,desttype=%s,name=\"%s\"", type, destinationName);
        listMBeanAttributes(pattern, excludeBrokerSpecific());
    }

    private void listAll() {
        listQueue();
        listTopic();
    }

    private void listQueue() {
        if(brokerType.equals("activemq"))
            listDestination("Queue");
        else if(brokerType.equals("openmq"))
            listDestination("q");
    }

    private void listTopic() {
        if(brokerType.equals("activemq"))
            listDestination("Topic");
        else if(brokerType.equals("openmq"))
            listDestination("t");
    }

    ////////////////////////////////////////////////////////////////////////////////

    public MistBroker() {
        brokerType = "openmq";
        jmxAddress.set(Utils.getHostIP() + ":" + Daemon.propMIST.getProperty("spy.monitor.jmxport"));
        jmxAuth.set(Daemon.propMIST.getProperty("spy.monitor.jmxauth"));
    }

    public QueryExp excludeBrokerSpecific() {
        if(brokerType.equals("openmq"))
            return Query.not(Query.initialSubString(new AttributeValueExp("Name"), new StringValueExp("mq.")));
        return Query.not(Query.initialSubString(new AttributeValueExp("Name"), new StringValueExp("ActiveMQ")));
    }

    public void run(String argv[]) throws Exception {
        LongOpt[] longopts = new LongOpt[] {
            new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'), 
            new LongOpt("auth", LongOpt.REQUIRED_ARGUMENT, null, 'u'), 
            new LongOpt("host", LongOpt.REQUIRED_ARGUMENT, null, 'b'), 
            new LongOpt("all", LongOpt.OPTIONAL_ARGUMENT, null, 'a'), 
            new LongOpt("queue", LongOpt.OPTIONAL_ARGUMENT, null, 'q'), 
            new LongOpt("topic", LongOpt.OPTIONAL_ARGUMENT, null, 't'), 
            new LongOpt("broker", LongOpt.NO_ARGUMENT, null, 'i'), 
            new LongOpt("type", LongOpt.REQUIRED_ARGUMENT, null, 'y'),
        };

        Getopt g = new Getopt("mist-broker", argv, "hu:b:iy:", longopts);
        int c;
        String arg = null;
        while((c = g.getopt()) != -1) {
            switch(c) {
            case 'b':
                jmxAddress.set(g.getOptarg());
                break;
            case 'y':
                brokerType = g.getOptarg();
                break;
            case 'u':
                jmxAuth.set(g.getOptarg());
                break;
            case 'a':
                currCmd = CmdType.ALL;
                arg = g.getOptarg();
                if(arg != null)
                    destinationName = arg;
                break;
            case 'q':
                currCmd = CmdType.QUEUE;
                arg = g.getOptarg();
                if(arg != null)
                    destinationName = arg;
                break;
            case 't':
                currCmd = CmdType.TOPIC;
                arg = g.getOptarg();
                if(arg != null)
                    destinationName = arg;
                break;
            case 'i':
                currCmd = CmdType.BROKER;
                break;
            case 'h':
                currCmd = CmdType.HELP;
                break;
            case '?':
                currCmd = CmdType.HELP;
                break;
            }
        }

        if(currCmd == CmdType.HELP)
            printUsage();
        else {
            brokerSpy = new BrokerSpy(brokerType, jmxAddress.toString(), jmxAuth.toString());
            try {
                brokerSpy.jmxConnectServer();
                if(currCmd == CmdType.ALL)
                    listAll();
                else if(currCmd == CmdType.QUEUE)
                    listQueue();
                else if(currCmd == CmdType.TOPIC)
                    listTopic();
                else if(currCmd == CmdType.BROKER)
                    listBrokerInfo();
            }
            catch(Exception e) {
                System.err.println(e.getMessage());
            }
            finally {
                brokerSpy.jmxCloseServer();
            }
        }
    }
    
    public static void main(String argv[]) throws Exception {
        myApp = new MistBroker();
        myApp.run(argv);
        System.exit(myApp.retValue);
    }
}
