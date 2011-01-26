package com.trendmicro.mist;

public class ExchangeMetric {
    private long msgOut = 0;
    private long msgOutBytes = 0;
    private long msgIn = 0;
    private long msgInBytes = 0;
    private long gocReference = 0;
    private long gocDeReference = 0;
    
    ////////////////////////////////////////////////////////////////////////////////

    public ExchangeMetric() {
    }
    
    public synchronized void increaseMessageIn(long size) {
        msgIn++;
        msgInBytes += size;
    }

    public synchronized void addMessageIn(long cnt, long size) {
        msgIn += cnt;
        msgInBytes += size;
    }

    public synchronized void increaseMessageOut(long size) {
        msgOut++;
        msgOutBytes += size;
    }

    public synchronized void addMessageOut(long cnt, long size) {
        msgOut += cnt;
        msgOutBytes += size;
    }

    public synchronized void resetMessageIn() {
        msgIn = 0;
        msgInBytes  = 0;
    }
    
    public synchronized void resetMessageOut() {
        msgOut = 0;
        msgOutBytes  = 0;
    }
    
    public synchronized void increaseGOCRef() {
        gocReference++;
    }

    public synchronized void increaseGOCDeRef() {
        gocDeReference++;
    }

    public synchronized void resetGOCRef() {
        gocReference = 0;
    }

    public synchronized void resetGOCDeRef() {
        gocDeReference = 0;
    }

    public long getMessageOutCount() {
        return msgOut;
    }

    public long getMessageOutBytes() {
        return msgOutBytes;
    }
    
    public long getMessageInCount() {
        return msgIn;
    }

    public long getMessageInBytes() {
        return msgInBytes;
    }
    
    public long getGOCReferenceCount() {
        return gocReference;
    }
    
    public long getGOCDeReferenceCount() {
        return gocDeReference;
    }
}
