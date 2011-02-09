package com.trendmicro.mist;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.UUID;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.protobuf.ByteString;
import com.trendmicro.mist.mfr.BrokerFarm;
import com.trendmicro.mist.mfr.ExchangeFarm;
import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.proto.MistMessage;
import com.trendmicro.mist.proto.ZooKeeperInfo;
import com.trendmicro.mist.proto.MistMessage.KeyValuePair;
import com.trendmicro.mist.util.ConnectionList;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.GOCUtils;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.spn.common.util.Utils;
import com.trendmicro.spn.proto.SpnMessage.Container;
import com.trendmicro.spn.proto.SpnMessage.LogInfo;

@Deprecated
public class Session implements Runnable, MessageListener {
    private GateTalk.Session sessionConfig;
    private static Object sessionIdCntLock = new Object();
    private static Integer sessionIdCnt = Math.abs(new Long(UUID.randomUUID().getLeastSignificantBits()).intValue());
    private int session_id;
    private static Log logger = LogFactory.getLog(Session.class);
    private Thread hostThread;
    private boolean detachNow = false;
    private boolean attached = false;
    private ServerSocket localServer;
    private boolean requestPause = false;
    private boolean paused = false;
    private GateTalk.Request.Role myRole = GateTalk.Request.Role.SESSION;
    private HashMap<String, Client> allClients = new HashMap<String, Client>();
    private BufferedOutputStream socketOutput = null;
    private BufferedInputStream socketInput = null;
    private boolean determined = false;
    private boolean isReady = false;
    private boolean producerEnd = false;
    private BufferedInputStream producerin = null;
    private boolean unack = false;
    private boolean doPause = false;
    
    private GOCUtils goc_server = null;
    
    private void detachMyself() {
        if(detachNow)
            return;
        GateTalk.Request.Builder req_builder = GateTalk.Request.newBuilder();
        req_builder.setType(GateTalk.Request.Type.CLIENT_DETACH);
        req_builder.setArgument(String.valueOf(String.valueOf(session_id)));
        GateTalk.Command.Builder cmd_builder = GateTalk.Command.newBuilder();
        cmd_builder.addRequest(req_builder.build());

        Socket sock = null;
        try {
            sock = new Socket();
            sock.setReuseAddress(true);
            sock.setTcpNoDelay(true);
            sock.connect(new InetSocketAddress("127.0.0.1", Daemon.DAEMON_PORT));
            Packet pack = new Packet();
            pack.setPayload(cmd_builder.build().toByteArray());
            pack.write(new BufferedOutputStream(sock.getOutputStream()));
            pack.read(new BufferedInputStream(sock.getInputStream()));
        }
        catch(IOException e) {
            logger.error(e.getMessage());
        }
        finally {
            try {
                sock.close();
            }
            catch(IOException e) {
                logger.error(e.getMessage());
            }
        }
    }
    
    public Collection<Client> getClientList() {
        return allClients.values();
    }

    private void doSuspend() {
        logger.warn(String.format("session %d: close all clients", getId()));
        close(true);
        logger.warn(String.format("session %d: paused", getId()));

        paused = true;
        do {
            Utils.justSleep(50);
        } while(requestPause);
        paused = false;

        logger.warn(String.format("session %d: continued", getId()));
        logger.warn(String.format("session %d: open all clients", getId()));
        open(true);
    }

    private void runConsumer() {
        Socket socket = null;
        try {
            isReady = true;
            localServer.setSoTimeout(1000);
            for(;;) {
                if(detachNow)
                    return;
                try {
                    socket = localServer.accept();
                    break;
                }
                catch(SocketTimeoutException e) {
                }
            }
            socket.setTcpNoDelay(true);
            socketInput = new BufferedInputStream(socket.getInputStream());
            socketOutput = new BufferedOutputStream(socket.getOutputStream());
            for(Client c:allClients.values())
                c.getConsumer().setMessageListener(this);
            do {
                Utils.justSleep(50);
                if(doPause) {
                    doSuspend();
                    doPause = false;
                    for(Client c:allClients.values()){
                        try {
                            c.getConsumer().setMessageListener(this);
                        }
                        catch(Exception e) {
                            logger.error(Utils.convertStackTrace(e));
                        }
                    }
                }
            } while(!detachNow);
        }
        catch(Exception e) {
            if(e instanceof IOException) {
                if(detachNow) {
                    try {
                        for(Client c : allClients.values())
                            c.getConsumer().setMessageListener(null);
                    }
                    catch(Exception e2) {
                        logger.error(e2.getMessage());
                    }
                }
                else {
                    logger.error(Utils.convertStackTrace(e));
                }
            }
            else {
                logger.error(Utils.convertStackTrace(e));
            }
        }
        finally {
            if(socket != null) {
                for(int i = 0; i < 20; i++) {
                    if(!unack)
                        break;
                    Utils.justSleep(500);
                }
                try {
                    socketInput.close();
                    socketOutput.close();
                    socket.close();
                }
                catch(IOException e) {
                    logger.error(e.getMessage());
                }
            }
            socketInput = null;
            socketOutput = null;
        }     
    }
    
    private Client mountExchangeForProducer(String name) throws MistException {
        GateTalk.Channel.Builder ch_builder = GateTalk.Channel.newBuilder();
        if(name.startsWith("topic:")) {
            ch_builder.setType(GateTalk.Channel.Type.TOPIC);
            ch_builder.setName(name.substring(6));
        } 
        else { 
            ch_builder.setType(GateTalk.Channel.Type.QUEUE);
            if(name.startsWith("queue:")) 
                ch_builder.setName(name.substring(6));
            else
                ch_builder.setName(name);
        }
        GateTalk.Client.Builder cl_builder = GateTalk.Client.newBuilder(); 
        cl_builder.setSessionId(session_id);
        cl_builder.setChannel(ch_builder.build());
        cl_builder.setType(GateTalk.Client.Type.PRODUCER);
        cl_builder.setAction(GateTalk.Client.Action.MOUNT);
        GateTalk.Client client_config = cl_builder.build();
        
        if((!getConfig().getConnection().getBrokerType().equals("activemq"))
            && (!Exchange.isValidExchange(client_config.getChannel().getName())))
                throw new MistException(String.format("exchange `%s' not valid", client_config.getChannel().getName()));

        Client client = new Client(client_config, getConfig());
        try {
            ZooKeeperInfo.TLSConfig tlsConfig = null;
            tlsConfig = ExchangeFarm.getInstance().belongsTLS((client.isQueue() ? "queue:": "topic:") + client.getChannelName());
            if(tlsConfig != null)
                addTlsClient(client, tlsConfig);
            client.openClient(isDetermined(), false, false);
            addClient(client);
            
            logger.info(String.format("session %d: create exchange `%s:%s'", getId(), client.isQueue() ?  "queue": "topic", client.getChannelName()));
        }
        catch(MistException e) {
            logger.error(e.getMessage());
            throw e;
        }
        return client;
    }

    private void runProducer() {
        Socket socket = null;
        BufferedOutputStream out = null;
        try {
            isReady = true;
            localServer.setSoTimeout(1000);
            for(;;) {
                if(detachNow)
                    return;
                try {
                    socket = localServer.accept();
                    break;
                }
                catch(SocketTimeoutException e) {
                }
            }
            socket.setTcpNoDelay(true);
            producerin = new BufferedInputStream(socket.getInputStream());
            out = new BufferedOutputStream(socket.getOutputStream());
            Packet pack = new Packet();
            int rdcnt = -1;
            int cnt = 0;
            do {
                if((rdcnt = pack.read(producerin)) > 0) {
                    logger.debug("message received");
                    try {
                        HashMap<String, String> props = new HashMap<String, String>();
                        long msg_ttl = Message.DEFAULT_TIME_TO_LIVE;
                        Client c = null;
                        long send_size = -1;
                        boolean msg_raw = false;
                        MistMessage.MessageBlock msg_block = null;
                        boolean needGOC = false;
                        boolean packedGOC = false;

                        MistMessage.MessageBlock.Builder mblock_builder = MistMessage.MessageBlock.newBuilder();
                        try {
                            mblock_builder.mergeFrom(pack.getPayload());
                            msg_block = mblock_builder.build();
                        }
                        catch(Exception e) {
                            logger.error(Utils.convertStackTrace(e));
                            pack.setPayload(GateTalk.Response.newBuilder().setSuccess(false).setException("unable to parse mist message block").build().toByteArray());
                            pack.write(out);
                            continue;
                        }

                        if(msg_block.hasTtl()) {
                            msg_ttl = msg_block.getTtl();
                            props.put("MIST_TTL", new Long(msg_ttl).toString());
                        }

                        pack.setPayload(msg_block.getMessage().toByteArray());

                        if(msg_block.getPropertiesCount() > 0) {
                            for(KeyValuePair pair : msg_block.getPropertiesList())
                                props.put(pair.getKey(), pair.getValue());
                        }

                        send_size = msg_block.toByteArray().length;
                        needGOC = (ExchangeFarm.getInstance().belongsGOC(msg_block.getId()) && msg_block.getMessage().toByteArray().length > Daemon.GOC_UPLOAD_SIZE);
                        packedGOC = true;
                        if(needGOC) {
                            long expire = (new Date().getTime() + (msg_block.hasTtl() ? msg_block.getTtl(): 900 * 1000)) / 1000;
                            if(goc_server == null)
                                goc_server = new GOCUtils();
                            byte[] msg_packed = goc_server.GOCPack(msg_block.getMessage().toByteArray(), expire);
                            if(msg_packed == null)
                                packedGOC = false;
                            else
                                pack.setPayload(msg_packed);
                        }

                        if(needGOC && !packedGOC) {
                            pack.setPayload(GateTalk.Response.newBuilder().setSuccess(false).setException("unable to upload GOC").build().toByteArray());
                            pack.write(out);
                            logger.error(String.format("session %d: unable to upload GOC", session_id));
                            continue;
                        }

                        c = findClient(new Exchange(msg_block.getId()));
                        if(c == null) {
                            logger.info(String.format("session %d: exchange `%s' not exist, auto mount", getId(), msg_block.getId()));
                            c = mountExchangeForProducer(msg_block.getId());
                        }
                        
                        int msgMaxSize;
                        if(c.getConnection().isOpenMQCluster())
                            msgMaxSize = Daemon.MAX_MESSAGE_SIZE;
                        else
                            msgMaxSize = Daemon.MAX_TRANSMIT_MESSAGE_SIZE;
                        if(pack.getPayload().length > msgMaxSize)
                            throw new MistException(String.format("sending %d bytes not allowed (exceeds %d bytes)", pack.getPayload().length, msgMaxSize));

                        for(;;) {
                            try {
                                c.getProducer().setTimeToLive(msg_ttl);
                                
                                if(props.size() > 0)
                                    c.sendMessageBytes(pack.getPayload(), props);
                                else
                                    c.sendMessageBytes(pack.getPayload());
                                logger.debug("message sent to broker");

                                Daemon.getExchangeMetric(c.getExchange()).increaseMessageOut(send_size);
                                if(!msg_raw) {
                                    if(c.tlsClient != null)
                                        sendTlsMessage(c.tlsClient, msg_block, "send");
                                    if(needGOC && packedGOC)
                                        Daemon.getExchangeMetric(c.getExchange()).increaseGOCRef();
                                }

                                pack.setPayload(GateTalk.Response.newBuilder().setSuccess(true).build().toByteArray());
                                pack.write(out);
                                logger.debug("ack written to client");
                                cnt++;
                                break;
                            }
                            catch(Exception e) {
                                if(e instanceof MistException || e instanceof JMSException || e instanceof javax.jms.IllegalStateException) {
                                    logger.warn("runProducer(): producer failed to deliver message, retry... \nStacktrace:" + Utils.convertStackTrace(e));
                                    Utils.justSleep(500);
                                    if(!c.getConnection().isOpenMQCluster())
                                        doSuspend();
                                    continue;
                                }
                                throw e;
                            }
                        }
                    }
                    catch(Exception e) {
                        logger.error(e.toString());
                        pack.setPayload(GateTalk.Response.newBuilder().setSuccess(false).setException(e.toString()).build().toByteArray());
                        pack.write(out);
                    }
                }
                else if(rdcnt == 0) {
                    pack.setPayload(GateTalk.Response.newBuilder().setSuccess(false).setException("invalid message size").build().toByteArray());
                    pack.write(out);
                }
                if(doPause) {
                    doSuspend();
                    doPause = false;
                }
            } while(rdcnt != -1);
            Packet.writeSize(out, -1);
        }
        catch(IOException e) {
            if(detachNow)
                return;

            logger.fatal(e.getMessage());
        }
        finally {
            producerEnd = true;
            if(socket != null) {
                try {
                    producerin.close();
                    out.close();
                    socket.close();
                }
                catch(IOException e) {
                    logger.error(e.getMessage());
                }
            }
        }
    }

    private void checkClientRole(GateTalk.Request.Role role) throws MistException {
        if(roleDecided()) {
            if((asConsumer() && role == GateTalk.Request.Role.SINK) || (!asConsumer() && role == GateTalk.Request.Role.SOURCE))
                throw new MistException(String.format("incompatible type, session is mounted as %s", asConsumer() ? "consumer": "producer"));
        }
    }
    
    private boolean roleDecided() {
        return myRole != GateTalk.Request.Role.SESSION;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public Client findClient(Exchange exchange) {
        return allClients.get(exchange.toString());
    }

    public UUID bigLock = UUID.randomUUID();

    public Session(GateTalk.Session sess_config) throws MistException {
        sessionConfig = sess_config;
        synchronized(sessionIdCntLock) {
            if(sessionIdCnt == Integer.MAX_VALUE)
                sessionIdCnt = 0;
            session_id = sessionIdCnt++;
        }
        if(!sessionConfig.getConnection().getHostName().equals("")) {
            GateTalk.Connection conn = sessionConfig.getConnection();
            ConnectionList connList = new ConnectionList();
            connList.set(conn.getHostName(), conn.getHostPort());
            if(!BrokerFarm.authenticateBroker(conn.getBrokerType(), connList, conn.getUsername(), conn.getPassword()))
                throw new MistException(String.format("can not connect `%s' with account `%s:%s'", connList.toString(), conn.getUsername(), conn.getPassword()));
            determined = true;
            logger.info(String.format("session %d is determined, target on %s", session_id, sessionConfig.getConnection().getHostName()));
        }
    }

    public String getCommPort() {
        return String.valueOf(localServer.getLocalPort());
    }
    
    public void attach(GateTalk.Request.Role role, boolean isDiscrete) throws MistException {
        while(attached && (asConsumer() ? detachNow: (producerEnd ? true: detachNow)))
            Utils.justSleep(100);

        isReady = false;

        if(attached)
            throw new MistException("already attached");

        try {
            checkClientRole(role);
            attached = true;
            localServer = new ServerSocket();
            localServer.setReuseAddress(true);
            localServer.bind(null);

            myRole = role;
            createThread();
            startThread();
            while(!isReady)
                Utils.justSleep(100);
        }
        catch(IOException e) {
            logger.error(e.getMessage());
            throw new MistException(e.getMessage());
        }
        catch(MistException e) {
            throw e;
        }
    }

    public void detach(GateTalk.Request.Role role) throws MistException {
        if(!attached)
            return;

        try {
            checkClientRole(role);
            detachNow = true;
            producerEnd = false;
            
            if(!asConsumer()) {
                try {
                    producerin.close();
                    close(false);
                }
                catch(Exception e) {
                }
            }

            if(role == GateTalk.Request.Role.SOURCE) {
                for(;;) {
                    if(hostThread == null)
                        break;
                    if(hostThread.isAlive()) {
                        Utils.justSleep(100);
                        continue;
                    }
                    break;
                }
            }
        }
        catch(MistException e) {
            throw e;
        }
        catch(Exception e) {
            logger.error(e.getMessage());
        }
    }
    
    public void addTlsClient(Client client, ZooKeeperInfo.TLSConfig tlsConfig) {
        GateTalk.Channel channel = GateTalk.Channel.newBuilder().setName(tlsConfig.getLogChannel()).setPersistent(false).setType(GateTalk.Channel.Type.QUEUE).build();
        GateTalk.Client clientCfg = GateTalk.Client.newBuilder().setAction(GateTalk.Client.Action.MOUNT).setType(GateTalk.Client.Type.PRODUCER).setChannel(channel).setSessionId(-1).build();

        GateTalk.Connection.Builder conn_builder = GateTalk.Connection.newBuilder();
        conn_builder.setHostName("").setHostPort("").setUsername("").setPassword("").setBrokerType("");
        GateTalk.Session.Builder sess_builder = GateTalk.Session.newBuilder();
        sess_builder.setConnection(conn_builder.build());

        Client tlsClient = new Client(clientCfg, sess_builder.build());
        tlsClient.tlsConfig = tlsConfig;
        client.tlsClient = tlsClient;
        try {
            client.tlsClient.openClient(isDetermined(), false, false);
        }
        catch(Exception e) {
            logger.error(e.getMessage());
        }
    }
    
    public void removeTlsClient(Exchange exchange) {
        Client c = findClient(exchange);
        if(c != null) {
            if(c.tlsClient != null) {
                c.tlsClient.closeClient(false, false);
                c.tlsClient = null;
            }
        }
    }
    
    public void open(boolean isResume) {
        for(Client c : allClients.values()) {
            try {
                ZooKeeperInfo.TLSConfig tlsConfig = null;
                tlsConfig = ExchangeFarm.getInstance().belongsTLS((c.isQueue() ? "queue:": "topic:") + c.getChannelName());
                if(tlsConfig != null)
                    addTlsClient(c, tlsConfig);
                c.openClient(isDetermined(), isResume, false);
            }
            catch(Exception e) {
                logger.error(e.getMessage());
            }
        }
    }
    
    public void close(boolean isPause) {
        for(Client c : allClients.values())
            c.closeClient(isPause, false);
    }
    
    public void sendTlsMessage(Client tlsClient, MistMessage.MessageBlock msg_block, String event) {
        try {
            Container.Builder tlsMsgBuilder = Container.newBuilder().mergeFrom(msg_block.getMessage().toByteArray());
            LogInfo.Builder logInfoBuilder = LogInfo.newBuilder();
            logInfoBuilder.setOriginalExchange(msg_block.getId());
            logInfoBuilder.setType(tlsClient.tlsConfig.getType());
            logInfoBuilder.setVersion(tlsClient.tlsConfig.getVersion());
            logInfoBuilder.setEvent(event);
            logInfoBuilder.setTimestamp(new Date().getTime());
            logInfoBuilder.setPrefix(tlsClient.tlsConfig.getPrefix());
            tlsMsgBuilder.setLogInfo(logInfoBuilder.build());
            tlsClient.sendMessageBytes(tlsMsgBuilder.build().toByteArray());
        }
        catch(Exception e) {
            logger.error("sendTlsMessage(): " + e.getMessage());
        }
    }

    private Exchange getMessageExchange(Message message) {
        Exchange exchange = new Exchange();
        try {
            Destination d = message.getJMSDestination();
            if(d instanceof Queue)
                exchange.set("queue:" + ((Queue) d).getQueueName());
            else if(d instanceof Topic)
                exchange.set("topic:" + ((Topic) d).getTopicName());
        }
        catch(JMSException e) {
        }
        return exchange;
    }

    public synchronized void onMessage(Message message) {
        logger.debug(String.format("session %d: received message from broker", session_id));
        if(detachNow) {
            logger.debug("detachnow, return");
            return;
        }

        Packet pack = new Packet();
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if(message instanceof BytesMessage) {
                byte [] block = new byte[256];
                int ret = -1;
                while((ret = ((BytesMessage) message).readBytes(block)) > 0)
                    bos.write(block, 0, ret);
            }
            else if(message instanceof TextMessage) {
                byte [] block = ((TextMessage) message).getText().getBytes("UTF-16");
                bos.write(block, 0, block.length);
            }
            if(bos.size() > 0)
                pack.setPayload(bos.toByteArray());
        }
        catch(Exception e) {
            logger.error(String.format("session %d: msg: %s;\n stacktrace: %s", session_id, e.getMessage(), Utils.convertStackTrace(e)));
            return;
        }

        Exchange exchange = getMessageExchange(message);

        if(pack.getPayload().length > 0) {
            MistMessage.MessageBlock.Builder msg_builder = MistMessage.MessageBlock.newBuilder();
            msg_builder.setId(exchange.toString());
            msg_builder.setMessage(ByteString.copyFrom(pack.getPayload()));

            boolean needGOC = false;
            boolean unpackedGOC = true;
            Container container = null;
            try {
                container = Container.newBuilder().mergeFrom(pack.getPayload()).build();
            }
            catch(Exception e) {
            }
            if(container != null)
                needGOC = (ExchangeFarm.getInstance().belongsGOC(exchange.getName()) || container.hasLogInfo()) && container.getContainerBase().hasMessageListRef();
            if(needGOC) {
                if(goc_server == null)
                    goc_server = new GOCUtils();
                byte[] msg_unpacked = goc_server.GOCUnPack(container.toByteArray());
                if(msg_unpacked == null)
                    unpackedGOC = false;
                else {
                    msg_builder.clearMessage();
                    msg_builder.setMessage(ByteString.copyFrom(msg_unpacked));

                    Daemon.getExchangeMetric(exchange).increaseGOCDeRef();
                }
            }

            if(needGOC && !unpackedGOC) {
                logger.error(String.format("session %d: unable to download from GOC", session_id));
                pack = null;
            }

            try {
                Enumeration<?> propNames = message.getPropertyNames();
                if(propNames.hasMoreElements()) {
                    while(propNames.hasMoreElements()) {
                        String key = (String) propNames.nextElement();
                        String value = message.getStringProperty(key);
                        if(key.equals("MIST_TTL"))
                            msg_builder.setTtl(Long.valueOf(value));
                        else
                            msg_builder.addProperties(KeyValuePair.newBuilder().setKey(key).setValue(value).build());
                    }
                }
            }
            catch(JMSException e) {
            }
            MistMessage.MessageBlock msg_block = msg_builder.build();

            Client c = findClient(exchange);
            if(c != null && c.tlsClient != null)
                sendTlsMessage(c.tlsClient, msg_block, "recv");

            pack.setPayload(msg_block.toByteArray());

            if(pack != null) {
                unack = true;
                try {
                    pack.write(socketOutput);
                }
                catch(IOException e) {
                    logger.error(String.format("session %d: client is broken, detach", session_id));
                    detachNow = true;
                    return;
                }
                Daemon.getExchangeMetric(exchange).increaseMessageIn(pack.getPayload().length);
                logger.debug(String.format("session %d: message delivered to client", session_id));

                for(;;) {
                    try {
                        if(pack.read(socketInput) <= 0) {
                            logger.error(String.format("session %d: client is broken, detach", session_id));
                            detachNow = true;
                            return;
                        }
                    }
                    catch(IOException e) {
                        logger.error(String.format("session %d: client is broken, detach", session_id));
                        detachNow = true;
                        logger.error(Utils.convertStackTrace(e));
                        return;
                    }
                    try {
                        if(GateTalk.Response.parseFrom(pack.getPayload()).getSuccess()) {
                            logger.debug(String.format("session %d: ack received", session_id));
                            break;
                        }
                    }
                    catch(Exception e) {
                        logger.fatal(String.format("session %d: %s", session_id, e.getMessage()));
                    }
                }
            }
        }

        // always need to ack message, even the message content is empty
        try {
            message.acknowledge();
            unack = false;
        }
        catch(JMSException e) {
            logger.error(String.format("session %d: msg acknowledge failed. msg: %s;\n stacktrace: %s", session_id, e.getMessage(), Utils.convertStackTrace(e)));
            Client c = findClient(exchange);
            if(c.getConnection().isOpenMQCluster()) {
                while(true) {
                    try {
                        // must do recover to clear failed state
                        c.getJMSSession().recover();
                    }
                    catch(JMSException e1) {
                        logger.error(String.format("session %d: session recovering failed, keep retrying.... msg: %s;\n stacktrace: %s", session_id, e1.getMessage(), Utils.convertStackTrace(e1)));
                        Utils.justSleep(1000);
                    }
                    break;
                }
            }
        }
    }

    public void run() {
        detachNow = false;
        producerEnd = false;
        open(false);
        if(roleDecided() && asConsumer()) {
            runConsumer();
        }
        else if(roleDecided() && !asConsumer()) {
            runProducer();
            if(!Daemon.isShutdownRequested())
                detachMyself();
        }
        close(false);

        try {
            localServer.close();
        }
        catch(IOException e) {
            logger.error(e.getMessage());
        }
        
        if(goc_server != null) {
            goc_server.close();
            goc_server = null;
        }
        attached = false;
    }
    
    public boolean isDetermined() {
        return determined;
    }

    public GateTalk.Session getConfig() {
        return sessionConfig;
    }

    public void createThread() {
        hostThread = new Thread(this, String.format("Session-%d", getId()));
    }

    public void startThread() {
        hostThread.start();
    }

    public void addClient(Client client) throws MistException {
        Exchange exchange = client.getExchange();
        if(allClients.size() == 0)
            allClients.put(exchange.toString(), client);
        else {
            if(roleDecided() && asConsumer() != client.isConsumer())
                throw new MistException("incompatible type, must be all consumer or all producer");
            if(allClients.containsKey(exchange.toString()))
                throw new MistException("exchange already mounted");
            allClients.put(exchange.toString(), client);
        }
        
        myRole = client.isConsumer() ? GateTalk.Request.Role.SOURCE: GateTalk.Request.Role.SINK;
        if(attached) {
            if(client.isConsumer()) {
                try {
                    client.getConsumer().setMessageListener(this);
                }
                catch(JMSException e) {
                    throw new MistException(e.getMessage());
                }
            }
        }
    }
    
    public void removeAllClient() {
        for(Client c : allClients.values())
            c.closeClient(false, false);
        allClients.clear();
        myRole = GateTalk.Request.Role.SESSION;
    }

    public void removeClient(GateTalk.Client client_config) throws MistException {
        if(allClients.size() == 0)
            throw new MistException("empty session");

        Exchange exchange = new Exchange((client_config.getChannel().getType() == GateTalk.Channel.Type.QUEUE ? "queue": "topic") + ":" + client_config.getChannel().getName());
        Client c = findClient(exchange);
        if(c == null)
            throw new MistException(String.format("exchange name `%s' not found", exchange.toString()));

        try {
            checkClientRole(client_config.getType() == GateTalk.Client.Type.CONSUMER ? GateTalk.Request.Role.SOURCE: GateTalk.Request.Role.SINK);
            c.closeClient(false, false);
            allClients.remove(exchange.toString());
            if(allClients.size() == 0)
                myRole = GateTalk.Request.Role.SESSION;
        }
        catch(MistException e) {
            throw e;
        }
    }

    public void setPause(boolean flag) {
        if(flag == true)
            doPause = true;
        requestPause = flag;
    }

    public boolean isPaused() {
        return paused;
    }

    public int getId() {
        return session_id;
    }

    public boolean isAttached() {
        return attached;
    }
    
    public Thread getThread() {
        return hostThread;
    }

    public boolean asConsumer() {
        return myRole == GateTalk.Request.Role.SOURCE;
    }
}
