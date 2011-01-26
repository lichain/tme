package com.trendmicro.mist.session;

import java.util.UUID;

/**
 * This class generates an unique incremental session id with a start value and
 * is thread-safe. When using the getInstance method, it will automatically
 * assign a different start value.
 */
public class UniqueSessionId {
    private static UniqueSessionId singleton = null;
    private int sessionId;

    public UniqueSessionId(int startValue) {
        sessionId = startValue;
    }

    public static UniqueSessionId getInstance() {
        synchronized(UniqueSessionId.class) {
            if(singleton == null)
                singleton = new UniqueSessionId(Math.abs(new Long(UUID.randomUUID().getLeastSignificantBits()).intValue()));
        }
        return singleton;
    }

    /**
     * Get a new session id
     * 
     * @return An integer session id in [1, Integer.MAX_VALUE]
     */
    public synchronized int getNewSessionId() {
        if(sessionId < 0)
            sessionId = 1;
        return sessionId++;
    }
}
