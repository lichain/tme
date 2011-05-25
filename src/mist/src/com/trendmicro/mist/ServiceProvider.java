package com.trendmicro.mist;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.session.ConsumerSession;
import com.trendmicro.mist.session.ProducerSession;
import com.trendmicro.mist.session.Session;
import com.trendmicro.mist.session.SessionPool;
import com.trendmicro.mist.session.UniqueSessionId;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.spn.common.util.Utils;

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
            int sessId = client_config.getSessionId();
            if(!Exchange.isValidExchange(client_config.getChannel().getName()))
                res_builder.setSuccess(false).setException(String.format("exchange `%s' not valid", client_config.getChannel().getName()));
            else if(!SessionPool.pool.containsKey(sessId))
                res_builder.setSuccess(false).setException(String.format("invalid session id %d", sessId));
            else {
                Exchange exchange = new Exchange(client_config.getChannel().getName());

                Session sess = SessionPool.getOrCreateConcreteSession(sessId, client_config.getType() == GateTalk.Client.Type.CONSUMER ? GateTalk.Request.Role.SOURCE: GateTalk.Request.Role.SINK);
                if(client_config.getAction() == GateTalk.Client.Action.MOUNT) {
                    Client client = sess.addClient(client_config);
                    res_builder.setSuccess(true).setContext(String.format("exchange %s mounted (%s)", exchange.toString(), client.getBrokerHost()));
                }
                else if(client_config.getAction() == GateTalk.Client.Action.UNMOUNT) {
                    sess.removeClient(client_config);
                    res_builder.setSuccess(true).setContext(String.format("exchange %s unmounted", exchange.getName()));
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
        int sess_id = UniqueSessionId.getInstance().getNewSessionId();
        synchronized(SessionPool.pool) {
            SessionPool.pool.put(sess_id, null);
        }
        res_builder.setSuccess(true).setContext(String.valueOf(sess_id));
        reply_builder.addResponse(res_builder.build());
    }

    private void requestSessionList(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        res_builder.setSuccess(true).setContext(SessionPool.getSessionListString());
    }

    private void requestSessionDestroy(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        int sess_id = Integer.parseInt(greq.getArgument());
        try {
            Session sess = SessionPool.getOrCreateConcreteSession(sess_id, null);
            if(sess != null) {
                if(sess instanceof ConsumerSession)
                    sess.detach(GateTalk.Request.Role.SOURCE);
                else if(sess instanceof ProducerSession)
                    sess.detach(GateTalk.Request.Role.SINK);
            }
            synchronized(SessionPool.pool) {
                SessionPool.pool.remove(sess_id);
            }
            res_builder.setSuccess(true).setContext(String.format("destroyed %d", sess_id));
        }
        catch(MistException e) {
            res_builder.setSuccess(false).setException(e.getMessage());
        }
    }

    private void requestSessionCleanFree(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        Iterator<Entry<Integer, Session>> iter = SessionPool.pool.entrySet().iterator();
        int ok_cnt = 0;
        try {
            while(iter.hasNext()) {
                Session sess = iter.next().getValue();
                if(sess == null) {
                    iter.remove();
                    ok_cnt++;
                }
                else if(!sess.isAttached()) {
                    iter.remove();
                    ok_cnt++;
                }
            }
        }
        catch(Exception e) {
        }
        res_builder.setSuccess(true).setContext(String.format("clean %d sessions", ok_cnt));
    }

    private void requestPing(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        res_builder.setSuccess(true).setContext(new StringBuffer(greq.getArgument()).reverse().toString());
    }

    private void requestSessionInfo(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        int sess_id = Integer.parseInt(greq.getArgument());
        try {
            Session sess = SessionPool.getOrCreateConcreteSession(sess_id, null);
            if(sess == null)
                throw new MistException("session " + sess_id + " has not been initialized yet!");
            String info = "";
            for(Client c : sess.getClientList())
                info += (c.getConnection().getActiveBroker() + " ");
            res_builder.setSuccess(true).setContext(info);
        }
        catch(MistException e) {
            res_builder.setSuccess(false).setException(e.getMessage());
        }
    }

    private void requestClientDetach(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        int sess_id = Integer.parseInt(greq.getArgument());
        try {
            Session sess = SessionPool.getOrCreateConcreteSession(sess_id, greq.getRole());
            sess.detach(greq.getRole());
            res_builder.setSuccess(true).setContext(String.format("detached %d", sess_id));
        }
        catch(MistException e) {
            res_builder.setSuccess(false).setException(e.getMessage());
        }
    }

    private void requestClientAttach(GateTalk.Request greq, GateTalk.Response.Builder res_builder) {
        int sess_id = Integer.parseInt(greq.getArgument());
        if(!SessionPool.pool.containsKey(sess_id))
            res_builder.setSuccess(false).setException("invalid session id " + sess_id);
        else {
            try {
                Session sess = SessionPool.getOrCreateConcreteSession(sess_id, greq.getRole());
                sess.attach(greq.getRole());
                res_builder.setSuccess(true).setContext(new Integer(sess.getCommPort()).toString());
            }
            catch(MistException e) {
                res_builder.setSuccess(false).setException(e.getMessage());
            }
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
            if(pack.read(in) > 0) {
                GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
                cmd_builder.mergeFrom(pack.getPayload());
                cmd = cmd_builder.build();
            }
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

    // //////////////////////////////////////////////////////////////////////////////

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
