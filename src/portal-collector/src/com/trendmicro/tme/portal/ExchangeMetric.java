package com.trendmicro.tme.portal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExchangeMetric {
    private String broker;
    private String type;
    private String name;
    private String rrd;
    private long timestamp = System.currentTimeMillis();
    private Map<String, String> metrics = new HashMap<String, String>();
    private List<String> consumers = new ArrayList<String>();
    private List<String> producers = new ArrayList<String>();
    
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
    
    public List<String> getConsumers() {
        return consumers;
    }
    
    public void addConsumer(String host) {
        consumers.add(host);
    }
    
    public List<String> getProducers() {
        return producers;
    }
    
    public void addProducer(String host) {
        producers.add(host);
    }
    
    public void addMetric(String key, String value) {
        metrics.put(key, value);
    }
}
