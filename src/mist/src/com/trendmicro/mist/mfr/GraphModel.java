package com.trendmicro.mist.mfr;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
public class GraphModel {
    private String name;
    private Set<String> processors = new HashSet<String>();
    private Set<String> rules = new HashSet<String>();
    private boolean enabled = false;
    
    public GraphModel() {
    }
    
    public GraphModel(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public Set<String> getProcessors() {
        return processors;
    }
    
    public void addProcessor(String processor) {
        processors.add(processor);
    }
    
    public void removeProcessor(String processor) {
        processors.remove(processor);
    }
    
    public void addRule(String rule) {
        rules.add(rule);
    }
    
    public void removeRule(String rule) {
        rules.remove(rule);
    }
    
    public Set<String> getRules() {
        return rules;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
