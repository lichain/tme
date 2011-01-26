package com.trendmicro.mist.cmd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ConnectException;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.ThreadInvoker;
import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.mist.util.ConnectionList;
import com.trendmicro.mist.util.Credential;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.spn.common.util.Utils;

public class MistSession extends ThreadInvoker {
    enum CmdType {
        LIST, 
        CREATE, 
        DESTROY, 
        CLEAN, 
        STATUS,
        HELP,
        INFO, 
        PING, 
    }
    
    enum RetVal {
        OK,
        CREATE_SESS_FAILED,
        DESTROY_SESS_FAILED,
        LIST_SESS_FAILED,
        GET_STATUS_FAILED,
        CLEAN_FREE_SESS_FAILED,
        INFO_SESS_FAILED,
        PING_FAILED,
    }

    private int targetSessionId = -1;

    private CmdType currCmd = CmdType.CREATE;
    private boolean hostSpecified = false;
    private GateTalk.Connection.Builder connBuilder = GateTalk.Connection.newBuilder();
    private ConnectionList connList = new ConnectionList();
    private Credential connAuth = new Credential();

    private void printUsage() {
        myOut.printf("Usage:%n");
        myOut.printf("      mist-session [options [arguments...] ]... %n%n");
        myOut.printf("Options: %n");
        myOut.printf("  --list, -l %n");
        myOut.printf("        list all current sessions %n%n");
        myOut.printf("  --create=HOST1:PORT[,HOST2:PORT], -c HOST1:PORT[,HOST2:PORT] %n");
        myOut.printf("        create a new session, multiple hosts to enable failover %n%n");
        myOut.printf("    --auth=USERNAME:PASSWORD, -a USERNAME:PASSWORD %n");
        myOut.printf("        specify account for authentication %n%n");
        myOut.printf("    --broker=[activemq|openmq], -b [activemq|openmq] %n");
        myOut.printf("        specify broker type %n%n");
        myOut.printf("  --destroy=SESSION_ID, -d SESSIONID %n");
        myOut.printf("        delete existing session %n%n");
        myOut.printf("  --clean, -e %n");
        myOut.printf("        remove free sessions %n%n");
        myOut.printf("  --info=SESSION_ID, -i SESSION_ID %n");
        myOut.printf("        get session info by ID %n%n");
        myOut.printf("  --status, -s %n");
        myOut.printf("        get daemon status %n%n");
        myOut.printf("  --help, -h %n");
        myOut.printf("        display help messages %n%n");
    }

    private void processList() {
        GateTalk.Request.Builder req_builder = GateTalk.Request.newBuilder();
        req_builder.setType(GateTalk.Request.Type.SESSION_LIST);

        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addRequest(req_builder.build());
        try {
            GateTalk.Response res = sendRequest(cmd_builder.build());
            if(res.getSuccess())
                myOut.printf("%s", res.getContext());
            else
                myErr.printf("failed: %s %n", res.getException());
            if(!res.getSuccess())
                exitCode = RetVal.LIST_SESS_FAILED.ordinal();
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
            exitCode = RetVal.LIST_SESS_FAILED.ordinal();
        }
    }

    private void processStatus() {
        GateTalk.Request.Builder req_builder = GateTalk.Request.newBuilder();
        req_builder.setType(GateTalk.Request.Type.DAEMON_STATUS);
        req_builder.setArgument("HELLO");

        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addRequest(req_builder.build());
        try {
            GateTalk.Response res = sendRequest(cmd_builder.build());
            if(res.getSuccess())
                myOut.printf("%s", res.getContext());
            else
                myErr.printf("failed: %s %n", res.getException());
            if(!res.getSuccess())
                exitCode = RetVal.GET_STATUS_FAILED.ordinal();
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
            exitCode = RetVal.GET_STATUS_FAILED.ordinal();
        }
    }

    private void processCreate() {
        GateTalk.Session.Builder sess_builder = GateTalk.Session.newBuilder();
        sess_builder.setConnection(connBuilder.build());
        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addSession(sess_builder.build());

        try {
            GateTalk.Response res = sendRequest(cmd_builder.build());
            if(res.getSuccess())
                myOut.printf("%s %n", res.getContext());
            else
                myErr.printf("failed: %s %n", res.getException());
            if(!res.getSuccess())
                exitCode = RetVal.CREATE_SESS_FAILED.ordinal();
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
            exitCode = RetVal.CREATE_SESS_FAILED.ordinal();
        }
    }

    private void processPing(String input) {
        GateTalk.Request.Builder req_builder = GateTalk.Request.newBuilder();
        req_builder.setType(GateTalk.Request.Type.PING);
        req_builder.setArgument(input);
        
        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addRequest(req_builder.build());
        try {
            GateTalk.Response res = sendRequest(cmd_builder.build());
            if(res.getSuccess()) 
                myOut.printf("%s%n", res.getContext());
            else {
                myErr.printf("failed: %s%n", res.getException());
                exitCode = RetVal.PING_FAILED.ordinal();
            }
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
        }
    }
    
    private void processInfo() {
        GateTalk.Request.Builder req_builder = GateTalk.Request.newBuilder();
        req_builder.setType(GateTalk.Request.Type.SESSION_INFO);
        req_builder.setArgument(String.valueOf(targetSessionId));
        
        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addRequest(req_builder.build());
        try {
            GateTalk.Response res = sendRequest(cmd_builder.build());
            if(res.getSuccess()) 
                myOut.printf("%s%n", res.getContext());
            else {
                myErr.printf("failed: %s%n", res.getException());
                exitCode = RetVal.INFO_SESS_FAILED.ordinal();
            }
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
        }
    }

    private void processDestroy() {
        GateTalk.Request.Builder req_builder = GateTalk.Request.newBuilder();
        req_builder.setType(GateTalk.Request.Type.SESSION_DESTROY);
        req_builder.setArgument(String.valueOf(targetSessionId));
        
        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addRequest(req_builder.build());
        try {
            GateTalk.Response res = sendRequest(cmd_builder.build());
            if(res.getSuccess()) 
                myOut.printf("%s%n", res.getContext());
            else {
                myErr.printf("failed: %s%n", res.getException());
                exitCode = RetVal.DESTROY_SESS_FAILED.ordinal();
            }
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
        }
    }

    private void processCleanFree() {
        GateTalk.Request.Builder req_builder = GateTalk.Request.newBuilder();
        req_builder.setType(GateTalk.Request.Type.SESSION_CLEAN_FREE);
        
        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addRequest(req_builder.build());
        try {
            GateTalk.Response res = sendRequest(cmd_builder.build());
            if(res.getSuccess()) 
                myOut.printf("%s%n", res.getContext());
            else {
                myErr.printf("failed: %s%n", res.getException());
                exitCode = RetVal.CLEAN_FREE_SESS_FAILED.ordinal();
            }
        }
        catch(MistException e) {
            myErr.println(e.getMessage());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public MistSession() {
        super("mist-session");
        if(!Daemon.isRunning()) { 
            myErr.println("Daemon not running");
            System.exit(-1);
        }
        exitCode = RetVal.OK.ordinal();
    }

    public int run(String argv[]) {
        LongOpt[] longopts = new LongOpt[] {
            new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'), 
            new LongOpt("list", LongOpt.NO_ARGUMENT, null, 'l'), 
            new LongOpt("auth", LongOpt.REQUIRED_ARGUMENT, null, 'a'), 
            new LongOpt("create", LongOpt.REQUIRED_ARGUMENT, null, 'c'), 
            new LongOpt("broker", LongOpt.REQUIRED_ARGUMENT, null, 'b'), 
            new LongOpt("destroy", LongOpt.REQUIRED_ARGUMENT, null, 'd'), 
            new LongOpt("info", LongOpt.REQUIRED_ARGUMENT, null, 'i'), 
            new LongOpt("status", LongOpt.NO_ARGUMENT, null, 's'), 
            new LongOpt("clean", LongOpt.NO_ARGUMENT, null, 'e'),
            new LongOpt("ping", LongOpt.REQUIRED_ARGUMENT, null, 'p'), 
        };

        Getopt g = new Getopt("mist-session", argv, "hla:c:d:seb:i:p:", longopts);
        int c;
        String arg = null;
        try {
            while((c = g.getopt()) != -1) {
                switch(c) {
                case 'c':
                    currCmd = CmdType.CREATE;
                    hostSpecified = true;
                    arg = g.getOptarg();
                    if(!Utils.checkSocketConnectable(arg))
                        throw new MistException(String.format("`%s' is not connectable", arg));
                    connList.merge(arg);
                    break;
                case 'd':
                    currCmd = CmdType.DESTROY;
                    arg = g.getOptarg();
                    targetSessionId = Integer.parseInt(arg);
                    break;
                case 'i':
                    currCmd = CmdType.INFO;
                    arg = g.getOptarg();
                    targetSessionId = Integer.parseInt(arg);
                    break;
                case 'a':
                    connAuth.set(g.getOptarg());
                    connBuilder.setUsername(connAuth.getUser());
                    connBuilder.setPassword(connAuth.getPassword());
                    break;
                case 'b':
                    arg = g.getOptarg().toLowerCase();
                    if(arg.equals("activemq") || arg.equals("openmq")) 
                        connBuilder.setBrokerType(arg);
                    else 
                        throw new MistException(String.format("unknown broker type: `%s' %n", arg));
                    break;
                case 'p':
                    arg = g.getOptarg();
                    currCmd = CmdType.PING;
                    break;
                case 'l':
                    currCmd = CmdType.LIST;
                    break;
                case 's':
                    currCmd = CmdType.STATUS;
                    break;
                case 'e':
                    currCmd = CmdType.CLEAN;
                    break;
                case 'h':
                    currCmd = CmdType.HELP;
                    break;
                case '?':
                    currCmd = CmdType.HELP;
                    break;
                }
            }
            
            if(hostSpecified) {
                String hosts = connList.get(0).getHost();
                String ports = connList.get(0).getPort();
                for(int i = 1; i < connList.size(); i++) {
                    hosts += ("," + connList.get(i).getHost());
                    ports += ("," + connList.get(i).getPort());
                }
                connBuilder.setHostName(hosts);
                connBuilder.setHostPort(ports);
            }
            else {
                connBuilder.setHostName("");
                connBuilder.setHostPort("");
                connBuilder.setUsername("");
                connBuilder.setPassword("");
                connBuilder.setBrokerType("");
            }

            if(currCmd == CmdType.LIST)
                processList();
            else if(currCmd == CmdType.STATUS)
                processStatus();
            else if(currCmd == CmdType.CREATE)
                processCreate();
            else if(currCmd == CmdType.DESTROY)
                processDestroy();
            else if(currCmd == CmdType.INFO)
                processInfo();
            else if(currCmd == CmdType.CLEAN)
                processCleanFree();
            else if(currCmd == CmdType.PING)
                processPing(arg);
            else if(currCmd == CmdType.HELP)
                printUsage();
        }
        catch(NumberFormatException e) {
            myErr.printf("%s, invalid number format %n", e.getMessage());
        }
        catch(Exception e) {
            myErr.println(e.getMessage());
            exitCode = RetVal.CREATE_SESS_FAILED.ordinal();
        }
        return exitCode;
    }

    public static void main(String argv[]) {
        MistSession sess = new MistSession();
        sess.run(argv);
        System.exit(sess.exitCode);
    }

    public static GateTalk.Response sendRequest(com.google.protobuf.Message message) throws MistException {
        try {
            Socket sock = new Socket();
            sock.setReuseAddress(true);
            sock.setTcpNoDelay(true);
            sock.connect(new InetSocketAddress("127.0.0.1", Daemon.DAEMON_PORT));

            Packet pack = new Packet();
            pack.setPayload(message.toByteArray());

            BufferedInputStream in = null;
            BufferedOutputStream out = null;
            try {
                in = new BufferedInputStream(sock.getInputStream());
                out = new BufferedOutputStream(sock.getOutputStream());
                pack.write(out);
                pack.read(in);
            }
            catch(IOException e) {
                throw new MistException(e.toString());
            }
            finally {
                in.close();
                out.close();
                sock.close();
            }

            GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
            cmd_builder.mergeFrom(pack.getPayload());
            GateTalk.Command cmd = cmd_builder.build();
            if(cmd.getResponseCount() > 0) {
                // assume always at least one response first, need revise later
                return cmd.getResponse(0);
            }
            else {
                GateTalk.Response.Builder res_builder = GateTalk.Response.newBuilder();
                res_builder.setSuccess(false).setException("empty response");
                return res_builder.build();
            }
        }
        catch(ConnectException e) {
            throw new MistException(e.getMessage() + ". MIST daemon not response.");
        }
        catch(IOException e) {
            throw new MistException(e.getMessage());
        }
    }

    public static GateTalk.Client makeClientRequest(int sess_id, Exchange exchange, boolean isConsumer, boolean isMount) {
        GateTalk.Channel.Builder channel_builder = GateTalk.Channel.newBuilder();
        channel_builder.setName(exchange.getName());
        if(exchange.isQueue())
            channel_builder.setType(GateTalk.Channel.Type.QUEUE);
        else
            channel_builder.setType(GateTalk.Channel.Type.TOPIC);

        GateTalk.Client.Builder client_builder = GateTalk.Client.newBuilder();
        client_builder.setSessionId(sess_id);
        client_builder.setChannel(channel_builder.build());
        
        if(isConsumer)
            client_builder.setType(GateTalk.Client.Type.CONSUMER);
        else
            client_builder.setType(GateTalk.Client.Type.PRODUCER);
        if(isMount)
            client_builder.setAction(GateTalk.Client.Action.MOUNT);
        else
            client_builder.setAction(GateTalk.Client.Action.UNMOUNT);
        
        return client_builder.build();
    }
}
