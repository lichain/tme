package com.trendmicro.tme.portal;

import java.util.HashMap;
import java.util.Map;

public class ExchangeMetric {
    private String broker;
    private String type;
    private String name;
    private String rrd;
    private long timestamp = System.currentTimeMillis();
    private Map<String, String> metrics = new HashMap<String, String>();
    
    public ExchangeMetric() {
    }
    
    public ExchangeMetric(String broker, String type, String name, String rrd) {
        this.broker = broker;
        this.type = type;
        this.name = name;
        this.rrd = rrd;
    }
    
    public String getBroker() {
        return broker;
    }
    
    public String getType() {
        return type;
    }
    
    public String getName() {
        return name;
    }
    
    public String getRrd() {
        return rrd;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public Map<String, String> getMetrics() {
        return metrics;
    }
    
    public void addMetric(String key, String value) {
        metrics.put(key, value);
    }
}
