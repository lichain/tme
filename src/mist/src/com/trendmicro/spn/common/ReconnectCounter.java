package com.trendmicro.spn.common;

import java.util.concurrent.TimeoutException;

public interface ReconnectCounter 
{
    public void init();
    public void waitAndCheckCounter() throws TimeoutException;
    public int getCounter();
}
