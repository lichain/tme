package com.trendmicro.nagios;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import com.trendmicro.tme.util.Utils;

import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.codi.ZNode;
import com.trendmicro.codi.CODIException;

public class ZkCheck {
	
	private String zkPath = "/tme2/local/nagios";
	private String zkHost = "localhost:2181";
	private final String zkNode = "TME 2.0 Nagios-plugin zk-check";
	
	private void printUsage() {
		System.out.printf("Usage:%n");
		System.out.printf("      check-zookeeper [options [arguments...] ]... %n%n");
		System.out.printf("Options: %n");
		System.out.printf("  --host={ip:port}, -h {ip:port}%n");
		System.out.printf("        the host of zookeeper [default:localhost:2181] %n%n");
		System.out.printf("  --help, -? %n");
		System.out.printf("        display help messages %n%n");
	}
	
	public ZkCheck() {
		
	}
	
	public void doCheck(String[] argv) {
		LongOpt[] longopts = new LongOpt[] {
				new LongOpt("help", LongOpt.NO_ARGUMENT, null, '?'),
				new LongOpt("host", LongOpt.REQUIRED_ARGUMENT, null, 'h'),
		};
		
		Getopt g = new Getopt("check-zookeeper", argv, "h:?", longopts);
		int c;
		while ((c = g.getopt()) != -1) {
			switch (c) {
			case 'h':
				zkHost = g.getOptarg();				
				break;
			case '?':
				printUsage();
				System.exit(0);
			}
		}

		if (zkHost == null) {
			System.err.println("no zookeeper server specified!");
			printUsage();
			System.exit(1);
		}
		
		try {		
            if(!Utils.checkSocketConnectable(zkHost))
                throw new Exception(String.format("unable to connect zookeeper"));

            ZKSessionManager.initialize(zkHost, 60000);
            ZKSessionManager session = ZKSessionManager.instance();
            ZNode znode = new ZNode(zkPath, session);
            znode.create(false, zkNode.getBytes());
			String res = new String(znode.getContent());
            znode.delete();
            ZKSessionManager.uninitialize();

			if(!res.equals(zkNode))
				System.exit(1);			
        }
        catch(Exception e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
        System.exit(0);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new ZkCheck().doCheck(args);
	}

}
