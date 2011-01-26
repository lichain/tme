package com.trendmicro.nagios;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.net.URI;
import java.util.Arrays;
import java.util.Random;

import com.trendmicro.tme.util.GOCUtils;
import com.trendmicro.tme.util.Utils;

public class DoteyCheck {
	private String targetHost = "localhost:80";
	private String targetURL = "";
	private byte [] postContent = new byte[1024];
	
	private String upload(byte [] content, long ttl) throws Exception {
	    String location = null;
        try {
            GOCUtils goc_server = new GOCUtils();
            location = goc_server.post(new URI(targetURL), content, ttl);
            goc_server.close();
        }
        catch(Exception e) {
            throw e;
        }
        return location;
    }

	private byte [] download(String url) throws Exception {
        byte [] object = null;
	    try {
	        GOCUtils goc_server = new GOCUtils();
	        object = goc_server.get(new URI(url));
	        goc_server.close();
	    }
        catch(Exception e) {
            throw e;
        }
        return object;
    }

	private void printUsage() {
		System.out.printf("Usage:%n");
		System.out.printf("      check-dotey [options [arguments...] ]... %n%n");
		System.out.printf("Options: %n");
		System.out.printf("  --host={ip:port}, -h {ip:port}%n");
		System.out.printf("        the host of dotey [default: %s]%n%n", targetHost);
		System.out.printf("  --help, -? %n");
		System.out.printf("        display help messages %n%n");
	}

    ////////////////////////////////////////////////////////////////////////////////
	
	public DoteyCheck() {
        Random rand = new Random();
        for(int i = 0; i < postContent.length; i++)
            postContent[i] = (byte)(Math.abs(rand.nextInt()) % 256);
	}

	public void doCheck(String[] argv) {
		LongOpt[] longopts = new LongOpt[] {
			new LongOpt("help", LongOpt.NO_ARGUMENT, null, '?'),
			new LongOpt("host", LongOpt.REQUIRED_ARGUMENT, null, 'h'),
		};
		
		Getopt g = new Getopt("check-dotey", argv, "h:?", longopts);
		int c;
		while((c = g.getopt()) != -1) {
			switch (c) {
			case 'h':
			    targetHost = g.getOptarg();				
				break;
			case '?':
				printUsage();
				System.exit(0);
			}
		}

		if(targetHost == null) {
			System.err.println("target host not specified!");
			System.exit(2);
		}
		
		try {
			if(!Utils.checkSocketConnectable(targetHost))
                throw new Exception(String.format("unable to connect host `%s'", targetHost));
			
			targetURL = String.format("http://%s/depot/*", targetHost);
			
			if(!Arrays.equals(postContent, download(upload(postContent, 300)))) 
			    throw new Exception("content not match");

			System.exit(0);
		}
		catch(Exception e) {
			System.exit(2);
		}		
	}

	public static void main(String[] args) {
		new DoteyCheck().doCheck(args);		
	}
}
