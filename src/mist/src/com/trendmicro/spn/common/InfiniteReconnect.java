package com.trendmicro.spn.common;

import java.util.Random;

public class InfiniteReconnect implements ReconnectCounter 
{
    private int nextDelay = 1;
    private int tryCount = 0;
    private int maxDelay = 900;
    private int delayUnit = 1000;
    private Random rand = new Random();

    public void init() {
        nextDelay = 1;
        tryCount = 0;
    }
    
    public void setDelayUnit(int unit) {
        if(unit > 0) 
            delayUnit = unit;
    }

    public void setMaxDelay(int delay) {
        if(delay > 0) 
            maxDelay = delay;
    }

    public void waitAndCheckCounter() {
        try {
            int myDelay = nextDelay * delayUnit;
            int delta = rand.nextInt() % (myDelay / 10);
            Thread.sleep(myDelay + delta);
            tryCount++;
        }
        catch(InterruptedException e) {
        }
        if(nextDelay * 2 <= maxDelay) 
            nextDelay *= 2;
        else 
            nextDelay = maxDelay;
    }

    public int getCounter() {
        return tryCount;
    }
}
