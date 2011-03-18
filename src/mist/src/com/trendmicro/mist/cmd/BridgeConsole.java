
package com.trendmicro.mist.cmd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.StringTokenizer;

import jline.ArgumentCompletor;
import jline.Completor;
import jline.NullCompletor;
import jline.SimpleCompletor;

import com.trendmicro.mist.console.Console;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.proto.BridgeTalk;
import com.trendmicro.mist.util.Packet;

public class BridgeConsole implements Console.CommandListener {
    private static BridgeConsole myApp;
    private Console myConsole = new Console("bridge-console");
    private int retVal = 0;

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                myApp.shutdown();
            }
        });
        System.out.printf("Welcome to the tme-bridge console!%n%n");

        System.out.printf("Connected to `%s:%s' ... ", TmeBridge.DAEMON_HOST, TmeBridge.DAEMON_PORT);
        try {
            BridgeTalk.Response response = sendRequest(BridgeTalk.Request.newBuilder().setCommand("COMPLETORS").build());
            String [] v = response.getContext().split(",");
            SimpleCompletor cmdCompletor = new SimpleCompletor(v[0].split(":"));
            SimpleCompletor argCompletor = new SimpleCompletor(v[1].split(":"));
            myConsole.setCompletor(new ArgumentCompletor(new Completor [] { cmdCompletor, argCompletor, new NullCompletor() }));
        }
        catch(MistException e) {
            System.out.printf("failed%n%s%n%n", e.getMessage());
            System.exit(1);
        }
        System.out.printf("success%n%n");
        System.out.printf("Type `help' for help message, `exit' to exit.%n");
    }

    private void shutdown() {
        System.out.printf("Bye-bye!%n");
    }
    
    private BridgeTalk.Response sendRequest(BridgeTalk.Request request) throws MistException {
        Socket connector = new Socket();
        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            connector = new Socket();
            connector.setReuseAddress(true);
            connector.setTcpNoDelay(true);
            connector.connect(new InetSocketAddress(TmeBridge.DAEMON_HOST, TmeBridge.DAEMON_PORT));
            in = new BufferedInputStream(connector.getInputStream());
            out = new BufferedOutputStream(connector.getOutputStream());
            
            Packet pack = new Packet();
            pack.setPayload(request.toByteArray());
            pack.write(out);
            pack.read(in);
            
            try {
                in.close();
                out.close();
                connector.close();
            }
            catch(IOException e) {
                System.out.println(e.getMessage());
            }
            
            return BridgeTalk.Response.newBuilder().mergeFrom(pack.getPayload()).build();
        }
        catch(Exception e) {
            throw new MistException(e.getMessage() + ". tme-bridge daemon not response.");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public BridgeConsole() {
    }

    public void start() {
        myConsole.setCommandListener(this);
        myConsole.receiveCommand();
        if (retVal != 0) System.exit(retVal);
    }

    public void processCommand(String command) {
        StringTokenizer tok = new StringTokenizer(command);
        if(tok.hasMoreElements()) {
            try {
                BridgeTalk.Response response = sendRequest(BridgeTalk.Request.newBuilder().setCommand(command).build());
                if(response.getSuccess()) {
                    retVal = 0;
                    myConsole.logResponse(response.getContext());
                    if(response.getContext().startsWith("EXIT") || response.getContext().endsWith("EXIT\n"))
                        System.exit(0);
                }
                else {
                    retVal = 1;
                    System.out.printf(response.getContext());
                }
            }
            catch(MistException e) {
                retVal = 1;
                System.out.println(e.getMessage());
            }
        }
    }

    public static void main(String [] argv) {
        myApp = new BridgeConsole();
        myApp.addShutdownHook();
        myApp.start();
    }
}
