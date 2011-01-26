package com.trendmicro.nagios;

import javax.jms.ConnectionFactory;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import com.trendmicro.tme.util.Utils;

public class BkCheck {
	
	private String bkHost = "localhost:7676";
	private String exchange = "com.trendmicro.tme.monitor.bounce";
	private boolean isQueue = true;
	private final String TextMsg = "Hello World!"; 
	
	private void printUsage() {
		System.out.printf("Usage:%n");
		System.out.printf("      check-broker [options [arguments...] ]... %n%n");
		System.out.printf("Options: %n");
		System.out.printf("  --host={ip:port}, -h {ip:port}%n");
		System.out.printf("        the host of broker [default:localhost:7676]%n%n");
		System.out.printf("  --help, -? %n");
		System.out.printf("        display help messages %n%n");
	}
	
	public BkCheck() {		
	}
	
	public void doCheck(String[] argv) {
		LongOpt[] longopts = new LongOpt[] {
				new LongOpt("help", LongOpt.NO_ARGUMENT, null, '?'),
				new LongOpt("host", LongOpt.REQUIRED_ARGUMENT, null, 'h'),
		};
		
		Getopt g = new Getopt("check-broker", argv, "h:?", longopts);
		int c;
		while ((c = g.getopt()) != -1) {
			switch (c) {
			case 'h':
				bkHost = g.getOptarg();				
				break;
			case '?':
				printUsage();
				System.exit(0);
			}
		}

		if (bkHost == null) {
			System.err.println("no broker server specified!");
			printUsage();
			System.exit(1);
		}
		
		try {
			if(!Utils.checkSocketConnectable(bkHost))
                throw new Exception(String.format("unable to connect broker"));
			String[] values = bkHost.split(":");
			ConnectionFactory conFact = new com.sun.messaging.ConnectionFactory();
            ((com.sun.messaging.ConnectionFactory) conFact).setProperty(com.sun.messaging.ConnectionConfiguration.imqBrokerHostName, values[0]);
            ((com.sun.messaging.ConnectionFactory) conFact).setProperty(com.sun.messaging.ConnectionConfiguration.imqBrokerHostPort, values[1]);
            ((com.sun.messaging.ConnectionFactory) conFact).setProperty(com.sun.messaging.ConnectionConfiguration.imqDefaultUsername, "admin");
            ((com.sun.messaging.ConnectionFactory) conFact).setProperty(com.sun.messaging.ConnectionConfiguration.imqDefaultPassword, "admin");
            javax.jms.Connection theConn = conFact.createConnection();
            theConn.start();

            javax.jms.Session theSess = theConn.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
            
            javax.jms.Destination theDest;
            if(isQueue)
                theDest = theSess.createQueue(exchange);
            else
                theDest = theSess.createTopic(exchange);

            javax.jms.MessageConsumer consumer = theSess.createConsumer(theDest);
            javax.jms.MessageProducer producer = theSess.createProducer(theDest);
            
            javax.jms.Message msgSend = theSess.createTextMessage();
            ((javax.jms.TextMessage)msgSend).setText(TextMsg);

           	producer.send(msgSend);
            javax.jms.Message msg = consumer.receive();
         
            //System.out.println("Message: " + ((javax.jms.TextMessage)msg).getText());
            
            String res =  ((javax.jms.TextMessage)msg).getText();

            consumer.close();
            producer.close();
            theSess.close();
            theConn.close();
            
            if (!res.equals(TextMsg))
            	System.exit(1);
        }
        catch(Exception e) {
        	System.exit(2);
        }
		
		System.exit(0);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new BkCheck().doCheck(args);
	}

}
