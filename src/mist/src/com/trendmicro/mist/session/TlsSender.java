package com.trendmicro.mist.session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.jms.Message;

import com.trendmicro.mist.MistException;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.spn.common.util.Utils;

public class TlsSender extends ProducerSession {
    static class TlsMessage {
        byte[] payload;
        Exchange exchange;

        public TlsMessage(byte[] payload, Exchange exchange) {
            this.payload = payload;
            this.exchange = exchange;
        }
    }

    private static TlsSender singleton = null;
    private static BlockingQueue<TlsMessage> msgBuffer = new ArrayBlockingQueue<TlsMessage>(100);
    List<Exchange> notRoutedDest = new ArrayList<Exchange>();

    private TlsSender() throws MistException {
        super(0, null);
        start();
    }

    private static void initialize() throws MistException {
        synchronized(TlsSender.class) {
            if(singleton == null)
                singleton = new TlsSender();
        }
    }

    @Override
    public void run() {
        for(;;) {
            TlsMessage tlsMsg = null;
            try {
                tlsMsg = msgBuffer.take();
            }
            catch(Exception e) {
                logger.error(Utils.convertStackTrace(e));
                continue;
            }
            try {
                notRoutedDest.clear();
                notRoutedDest.add(tlsMsg.exchange);
                deliverMessage(tlsMsg.payload, Message.DEFAULT_TIME_TO_LIVE, null, notRoutedDest);
            }
            catch(Exception e) {
                logger.error(Utils.convertStackTrace(e));
            }
        }
    }

    public static void reset() throws MistException {
        if(singleton != null)
            singleton.allClients.clear();
    }

    public static void writeTlsMessage(byte[] tlsMsg, Exchange tlsExchange, int timeout) throws MistException, InterruptedException, TimeoutException {
        initialize();

        if(!msgBuffer.offer(new TlsMessage(tlsMsg, tlsExchange), timeout, TimeUnit.MILLISECONDS))
            throw new TimeoutException("TLS channel is congested!");
    }
}
