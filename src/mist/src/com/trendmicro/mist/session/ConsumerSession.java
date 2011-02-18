package com.trendmicro.mist.session;

import java.io.IOException;
import java.util.Enumeration;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Topic;

import com.google.protobuf.ByteString;
import com.trendmicro.mist.Client;
import com.trendmicro.mist.ExchangeMetric;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.mfr.ExchangeFarm;
import com.trendmicro.mist.proto.GateTalk;
import com.trendmicro.mist.proto.MistMessage;
import com.trendmicro.mist.proto.MistMessage.KeyValuePair;
import com.trendmicro.mist.proto.ZooKeeperInfo.TLSConfig;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.mist.util.GOCUtils;
import com.trendmicro.mist.util.Packet;
import com.trendmicro.spn.common.util.Utils;
import com.trendmicro.spn.proto.SpnMessage;
import com.trendmicro.spn.proto.SpnMessage.Container;
import com.trendmicro.spn.proto.SpnMessage.LogInfo;

public class ConsumerSession extends Session implements MessageListener {
    public static class MessagePrepared {
        public MistMessage.MessageBlock msgBlock;
        public Exchange from;

        public SpnMessage.Container tlsMessage = null;
        public Exchange tlsExchange = null;
        public boolean isGocRef = false;

        private byte[] convertJMSMessage(BytesMessage msg) throws JMSException, IOException {
            ByteString.Output byteOut = ByteString.newOutput();
            byte[] buf = new byte[1024];
            int len = -1;
            while((len = msg.readBytes(buf)) > 0)
                byteOut.write(buf, 0, len);
            return byteOut.toByteString().toByteArray();
        }

        private void setupTlsMessage() {
            TLSConfig tlsConfig = ExchangeFarm.getInstance().belongsTLS(from.toString());
            if(tlsConfig == null)
                return;
            try {
                SpnMessage.Container.Builder tlsMsgBuilder = SpnMessage.Container.newBuilder().mergeFrom(msgBlock.getMessage());
                LogInfo.Builder logInfoBuilder = LogInfo.newBuilder();
                logInfoBuilder.setOriginalExchange(from.toString());
                logInfoBuilder.setType(tlsConfig.getType());
                logInfoBuilder.setVersion(tlsConfig.getVersion());
                logInfoBuilder.setEvent("recv");
                logInfoBuilder.setTimestamp(System.currentTimeMillis());
                logInfoBuilder.setPrefix(tlsConfig.getPrefix());
                tlsMsgBuilder.setLogInfo(logInfoBuilder.build());

                tlsMessage = tlsMsgBuilder.build();
                tlsExchange = new Exchange(tlsConfig.getLogChannel());
            }
            catch(Exception e) {
                logger.error("message to TLS exchange not packed as SPN Message: " + Utils.convertStackTrace(e));
            }
        }

        public MessagePrepared(BytesMessage msg, GOCUtils gocServer) throws MistException, JMSException, IOException {
            Destination d = msg.getJMSDestination();
            if(d instanceof Queue)
                from = new Exchange("queue:" + ((Queue) d).getQueueName());
            else if(d instanceof Topic)
                from = new Exchange("topic:" + ((Topic) d).getTopicName());

            MistMessage.MessageBlock.Builder builder = MistMessage.MessageBlock.newBuilder();
            builder.setId(from.toString());

            byte[] payload = convertJMSMessage(msg);

            /**
             * Try to parse the payload as SPNMessage, if not, just continue
             */
            try {
                Container container = Container.newBuilder().mergeFrom(payload).build();
                // Download from GOC if needed
                if(container.getContainerBase().hasMessageListRef()) {
                    if(ExchangeFarm.getInstance().belongsGOC(from.getName()) || container.hasLogInfo()) {
                        if(gocServer == null)
                            throw new MistException(String.format("unable to download from GOC: %s", container.getContainerBase().getMessageListRef().getUrl()));
                        payload = gocServer.GOCUnPack(payload);
                        isGocRef = true;
                    }
                }
                // Cannot download, throw exception
                if(payload == null)
                    throw new MistException(String.format("unable to download from GOC: %s", container.getContainerBase().getMessageListRef().getUrl()));
            }
            catch(Exception e) {
                if(e instanceof MistException)
                    throw (MistException) e;
            }
            builder.setMessage(ByteString.copyFrom(payload));

            Enumeration<?> propNames = msg.getPropertyNames();
            while(propNames.hasMoreElements()) {
                String key = (String) propNames.nextElement();
                String value = msg.getStringProperty(key);
                if(key.equals("MIST_TTL"))
                    builder.setTtl(Long.valueOf(value));
                else
                    builder.addProperties(KeyValuePair.newBuilder().setKey(key).setValue(value).build());
            }
            msgBlock = builder.build();

            setupTlsMessage();
        }
    }

    private Packet pack = new Packet();
    private String prefixThreadName = "";
    private boolean attached = false;

    public ConsumerSession(int sessId, GateTalk.Session sessConfig) throws MistException {
        super(sessId, sessConfig);
        prefixThreadName = "Session-" + sessId + "-";
    }

    @Override
    public void run() {
        if(!acceptConnection())
            return;
        isReady = true;
        attached = true;

        for(Client c : allClients.values()) {
            try {
                c.getConsumer().setMessageListener(this);
            }
            catch(Exception e) {
                logger.error(Utils.convertStackTrace(e));
            }
        }
    }

    @Override
    public void addClientIfAttached(Client c) {
        try {
            c.getConsumer().setMessageListener(this);
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
        }
    }

    @Override
    protected void detach() {
        attached = false;
    }

    private void onMessageProcess(Message msg) throws IOException {
        if(detachNow)
            return;
        MessagePrepared mp = null;
        try {
            mp = new MessagePrepared((BytesMessage) msg, getGocClient());
        }
        catch(Exception e) {
            logger.error(e.getMessage());
            try {
                msg.acknowledge();
            }
            catch(JMSException e1) {
            }
            return;
        }

        // Write TLS message
        if(mp.tlsMessage != null) {
            try {
                TlsSender.writeTlsMessage(mp.tlsMessage.toByteArray(), mp.tlsExchange, 5000);
            }
            catch(Exception e) {
                logger.error(Utils.convertStackTrace(e));
            }
        }

        pack.setPayload(mp.msgBlock.toByteArray());
        pack.write(socketOutput);
        
        ExchangeMetric metric = ExchangeMetric.getExchangeMetric(mp.from);
        metric.increaseMessageIn(mp.msgBlock.getMessage().size());
        if(mp.isGocRef)
            metric.increaseGOCDeRef();

        if(pack.read(socketInput) <= 0)
            return;

        try {
            if(GateTalk.Response.newBuilder().mergeFrom(pack.getPayload()).build().getSuccess())
                msg.acknowledge();
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
        }
    }

    @Override
    public synchronized void onMessage(Message msg) {
        String imqThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(prefixThreadName + imqThreadName);
        try {
            onMessageProcess(msg);
        }
        catch(IOException e) {
            logger.error(Utils.convertStackTrace(e));
            detach();
        }
        Thread.currentThread().setName(imqThreadName);
    }

    @Override
    public boolean isAttached() {
        return attached;
    }
}
