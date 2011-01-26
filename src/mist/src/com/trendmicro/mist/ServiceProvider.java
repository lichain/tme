package com.trendmicro.mist;

import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.net.Socket;
import java.net.ServerSocket;

import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.spn.common.util.Utils;

import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.MistException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ServiceProvider implements Runnable {
    private static Log logger = LogFactory.getLog(ServiceProvider.class);
    private ServerSocket server;
    private static Integer serviceIdCnt = 0;
    private int myId = -1;
    private boolean ready = false;
    private Thread hostThread;
    private boolean die = false;

    private void handleClient(GateTalk.Client client_config, GateTalk.Command.Builder reply_builder) {
        GateTalk.Response.Builder res_builder = GateTalk.Response.newBuilder();
        try {
            Session sess = Daemon.instance.getSessionById(client_config.getSessionId());
            if(!sess.getConfig().getConnection().getBrokerType().equals("activemq") 
                && !Exchange.isValidExchange(client_config.getChannel().getName())) 
                    res_builder.setSuccess(false).setException(String.format("exchange `%s' not valid", client_config.getChannel().getName()));            
            else {
                synchronized(sess.bigLock) {
                    if(client_config.getAction() == GateTalk.Client.Action.MOUNT) {
                        for(Client c : sess.getClientList()) {
                            if(c.getConfig().equals(client_config))
                                throw new MistException("exchange already mounted");
                        }
                        Client client = new Client(client_config, sess.getConfig());
                        client.openClient(sess.isDetermined(), false, false);
                        sess.addClient(client);
                        res_builder.setSuccess(true).setContext(String.format("mount %s:%s (%s)", client.isQueue() ? "queue": "topic", client.getChannelName(), client.getBrokerHost()));
                    }
                    else if(client_config.getAction() == GateTalk.Client.Action.UNMOUNT) {
                        sess.removeClient(client_config);
                        res_builder.setSuccess(true).setContext(String.format("unmount %s", client_config.getChannel().getName()));
                    }
                }
            }
        }
        catch(MistException e) {
            res_builder.setSuccess(false).setException(e.getMessage());
        }
        reply_builder.addResponse(res_builder.build());
    }

    private void handleSession(GateTalk.Session sess_config, GateTalk.Command.Builder reply_builder) {
        GateTalk.Response.Builder res_builder = GateTalk.Response.newBuilder();
        try {
            Session sess = new Session(sess_config);
            int sess_id = sess.getId();
            synchronized(Daemon.sessionPool) {
                Daemon.sessionPool.add(sess);
            }
            res_builder.setSuccess(true).setContext(String.valueOf(sess_id));
        }
        catch(MistException e) {
            res_builder.setSuccess(false).setException(e.getMessage());
        }
        reply_builder.addResponse(res_builder.build());
    }

    private void requestSessionList(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        res_builder.setSuccess(true).setContext(Daemon.instance.getSessionList());
    }

    private void requestSessionDestroy(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        int sess_id = Integer.parseInt(greq.getArgument());
        try {
            synchronized(Daemon.instance.getSessionById(sess_id).bigLock) {
                Daemon.instance.removeSession(sess_id);
            }
            res_builder.setSuccess(true).setContext(String.format("destroyed %d", sess_id));
        }
        catch(MistException e) {
            res_builder.setSuccess(false).setException(e.getMessage());
        }
    }

    private void requestSessionCleanFree(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        ArrayList<Integer> sessFree = Daemon.instance.getSessionIdFree();
        int i;
        int ok_cnt = 0;
        for(i = 0; i < sessFree.size(); i++) {
            try {
                Daemon.instance.removeSession(sessFree.get(i));
                ok_cnt++;
            }
            catch(MistException e) {
            }
        }
        res_builder.setSuccess(true).setContext(String.format("clean %d sessions", ok_cnt));
    }

    private void requestPing(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        res_builder.setSuccess(true).setContext(new StringBuffer(greq.getArgument()).reverse().toString());
    }
    
    private void requestSessionInfo(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        int sess_id = Integer.parseInt(greq.getArgument());
        try {
            Session sess = Daemon.instance.getSessionById(sess_id);
            String info = "";
            for(Client c: sess.getClientList())
                info += (c.getConnection().getActiveBroker() + " ");
            res_builder.setSuccess(true).setContext(info);
            Daemon.joinSession(sess.getThread());
        }
        catch(MistException e) {
            res_builder.setSuccess(false).setException(e.getMessage());
        }
    }

    private void requestClientDetach(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        int sess_id = Integer.parseInt(greq.getArgument());
        try {
            Session sess = Daemon.instance.getSessionById(sess_id);
            if(greq.getRole() == GateTalk.Request.Role.SINK || greq.getRole() == GateTalk.Request.Role.SOURCE) {
                synchronized(sess.bigLock) {
                    sess.detach(greq.getRole());
                }
            }
            else
                sess.detach(greq.getRole());
            res_builder.setSuccess(true).setContext(String.format("detached %d", sess_id));
            Daemon.joinSession(sess.getThread());
        }
        catch(MistException e) {
            res_builder.setSuccess(false).setException(e.getMessage());
        }
    }

    private void requestClientAttach(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        int sess_id = Integer.parseInt(greq.getArgument());
        try {
            Session sess = Daemon.instance.getSessionById(sess_id);
            synchronized(sess.bigLock) {
                sess.attach(greq.getRole(), false);
            }
            res_builder.setSuccess(true).setContext(sess.getCommPort());
        }
        catch(MistException e) {
            res_builder.setSuccess(false).setException(e.getMessage());
        }
    }
    
    private void requestDaemonStatus(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        String input = greq.getArgument();
        res_builder.setSuccess(true).setContext(Daemon.instance.getDaemonStatus(input));
    }

    private void handleRequest(GateTalk.Request greq, GateTalk.Command.Builder reply_builder) {
        GateTalk.Response.Builder res_builder = GateTalk.Response.newBuilder();
        if(greq.getType() == GateTalk.Request.Type.SESSION_LIST)
            requestSessionList(greq, res_builder);
        else if(greq.getType() == GateTalk.Request.Type.SESSION_DESTROY)
            requestSessionDestroy(greq, res_builder);
        else if(greq.getType() == GateTalk.Request.Type.SESSION_CLEAN_FREE)
            requestSessionCleanFree(greq, res_builder);
        else if(greq.getType() == GateTalk.Request.Type.CLIENT_ATTACH)
            requestClientAttach(greq, res_builder);
        else if(greq.getType() == GateTalk.Request.Type.CLIENT_DETACH)
            requestClientDetach(greq, res_builder);
        else if(greq.getType() == GateTalk.Request.Type.DAEMON_STATUS)
            requestDaemonStatus(greq, res_builder);
        else if(greq.getType() == GateTalk.Request.Type.SESSION_INFO)
            requestSessionInfo(greq, res_builder);
        else if(greq.getType() == GateTalk.Request.Type.PING)
            requestPing(greq, res_builder);
        reply_builder.addResponse(res_builder.build());
    }

    private void writeResponse(GateTalk.Command cmd, BufferedOutputStream out) {
        try {
            Packet pack = new Packet();
            pack.setPayload(cmd.toByteArray());
            pack.write(out);
        }
        catch(IOException e) {
            logger.error(e.getMessage());
        }
    }

    private GateTalk.Command readCommand(BufferedInputStream in) {
        GateTalk.Command cmd = null;
        try {
            Packet pack = new Packet();
            pack.read(in);

            GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
            cmd_builder.mergeFrom(pack.getPayload());
            cmd = cmd_builder.build();
        }
        catch(IOException e) {
            logger.error(e.getMessage());
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
        }
        return cmd;
    }

    synchronized public void setReady(boolean flag) {
        ready = flag;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public ServiceProvider(ServerSocket _server) {
        synchronized(serviceIdCnt) {
            myId = serviceIdCnt++;
        }
        server = _server;
    }

    synchronized public boolean isReady() {
        return ready;
    }

    public int serve(GateTalk.Command cmd, GateTalk.Command.Builder reply_builder) {
        int i, cmd_cnt = 0;

        for(i = 0; i < cmd.getClientCount(); i++)
            handleClient(cmd.getClient(i), reply_builder);
        cmd_cnt += cmd.getClientCount();

        for(i = 0; i < cmd.getSessionCount(); i++)
            handleSession(cmd.getSession(i), reply_builder);
        cmd_cnt += cmd.getSessionCount();

        for(i = 0; i < cmd.getRequestCount(); i++)
            handleRequest(cmd.getRequest(i), reply_builder);
        cmd_cnt += cmd.getRequestCount();

        return cmd_cnt;
    }

    public int getId() {
        return myId;
    }

    public void createThread(String name) {
        hostThread = new Thread(this, name);
    }

    public void startThread() {
        hostThread.start();
    }
    
    public void stopThread() {
        die = true;
    }
    
    public void run() {
        while(true) {
            Socket clientSocket = null;
            BufferedInputStream in = null;
            BufferedOutputStream out = null;
            try {
                setReady(true);
                synchronized(server) {
                    if(die)
                        break;
                    setReady(false);
                    clientSocket = server.accept();
                }

                in = new BufferedInputStream(clientSocket.getInputStream());
                out = new BufferedOutputStream(clientSocket.getOutputStream());
                GateTalk.Command gateCommand = readCommand(in);
                if(gateCommand != null) {
                    GateTalk.Command.Builder reply_builder = GateTalk.Command.newBuilder();
                    logger.info(String.format("ACCEPT - %s", gateCommand.toString().replaceAll("\n", "")));

                    GateTalk.Command cmd = null;
                    if(serve(gateCommand, reply_builder) > 0)
                        cmd = reply_builder.build();
                    writeResponse(cmd, out);
                    logger.info(String.format("answer - %s", cmd.toString().replaceAll("\n", "")));
                }
            }
            catch(IOException e) {
                logger.error(e.getMessage());
            }
            finally {
                try {
                    if(in != null) {
                        in.close();
                        out.close();
                        clientSocket.close();
                    }
                }
                catch(IOException e) {
                    logger.error(e.getMessage());
                }
            }
        }
        synchronized(Daemon.deadServiceList) {
            Daemon.deadServiceList.add(hostThread);
        }
    }
}
