package com.trendmicro.nagios;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import com.trendmicro.tme.util.Utils;

public class PtCheck {
	
	private String ptHost = "localhost:80";
	private String ptDir = "";
	
	private void printUsage() {
		System.out.printf("Usage:%n");
		System.out.printf("      check-portal [options [arguments...] ]... %n%n");
		System.out.printf("Options: %n");
		System.out.printf("  --host={ip:port}, -h {ip:port}%n");
		System.out.printf("        the host of portal [default:localhost:80]%n%n");
		System.out.printf("  --dir={directory}, -d {directory}%n");
		System.out.printf("        the host of portal %n%n");
		System.out.printf("  --help, -? %n");
		System.out.printf("        display help messages %n%n");
	}

	public void doCheck(String[] argv) {
		LongOpt[] longopts = new LongOpt[] {
				new LongOpt("help", LongOpt.NO_ARGUMENT, null, '?'),
				new LongOpt("host", LongOpt.REQUIRED_ARGUMENT, null, 'h'),
				new LongOpt("dir", LongOpt.REQUIRED_ARGUMENT, null, 'd'),
		};
		
		Getopt g = new Getopt("check-portal", argv, "h:d:?", longopts);
		int c;
		while ((c = g.getopt()) != -1) {
			switch (c) {
			case 'h':
				ptHost = g.getOptarg();				
				break;
			case 'd':
				ptDir = g.getOptarg();
				break;
			case '?':
				printUsage();
				System.exit(0);
			}
		}

		if (ptHost == null) {
			System.err.println("no portal server specified!");
			printUsage();
			System.exit(1);
		}
		
		try {
			if(!Utils.checkSocketConnectable(ptHost))
                throw new Exception(String.format("unable to connect portal server"));
			
			HttpClient httpClient = new DefaultHttpClient();
			URI uri = new URI("http://"+ptHost+"/"+ptDir);
			HttpGet httpget = new HttpGet(uri);
			
			// Execute the request
			HttpResponse response = httpClient.execute(httpget);
			
			int status = response.getStatusLine().getStatusCode();
			if (status != 200) {				
				throw new IOException( uri+": "+response.getStatusLine() + " status: " + status);
			}
			
			httpClient.getConnectionManager().shutdown();
			
			System.exit(0);

		}
		catch (IOException e) {
			System.exit(1);
		}
		catch (Exception e) {
			System.exit(2);
		}		
	}

	public PtCheck() {
		
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new PtCheck().doCheck(args);		
	}
}
