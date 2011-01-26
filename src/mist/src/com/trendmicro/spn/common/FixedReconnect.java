package com.trendmicro.spn.common;

import java.util.concurrent.TimeoutException;

public class FixedReconnect implements ReconnectCounter
{
    private int tryCount = 0;
    private int times = 0;
    private int interval = 0;

    public FixedReconnect(int count, int milliSecond) {
        setTimes(count);
        setInterval(milliSecond);
        init();
    }

    public void setTimes(int count) {
        if(count > 0)
            times = count;
    }

    public void setInterval(int milliSecond) {
        if(milliSecond > 0)
            interval = milliSecond;
    }

    public void init() {
        tryCount = 0;
    }

    public void waitAndCheckCounter() throws TimeoutException {
        if(times == 0)
        	throw new TimeoutException("ReconnectCounter: counter reaches");
        if(tryCount < times) {
            tryCount++;
            try {
                Thread.sleep(interval);
            }
            catch(InterruptedException e) {
            }
        }
        else 
        	throw new TimeoutException("ReconnectCounter: counter reaches");
    }

    public int getCounter() {
        return tryCount;
    }
}
