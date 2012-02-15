package com.trendmicro.mist.util;

import com.trendmicro.mist.MistException;
import com.trendmicro.mist.session.ConsumerSession;
import com.trendmicro.mist.session.ProducerSession;

public interface MessageFilter {
    public void preSend(ProducerSession.MessagePrepared msg) throws MistException;
    
    public void postReceive(ConsumerSession.MessagePrepared msg) throws MistException;
}
