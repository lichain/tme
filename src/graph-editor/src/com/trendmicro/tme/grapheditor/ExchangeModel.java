package com.trendmicro.tme.grapheditor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.graphviz.SWIGTYPE_p_Agnode_t;
import org.graphviz.SWIGTYPE_p_Agraph_t;
import org.graphviz.gv;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
public class ExchangeModel {
    public static enum Type {
        QUEUE, TOPIC,
    }
    
    private String name;
    private Type type = Type.QUEUE;
    
    public ExchangeModel(String name) {
        if(name.startsWith("queue:")) {
            this.name = name.substring(6);
        }
        else if(name.startsWith("topic:")) {
            type = Type.TOPIC;
            this.name = name.substring(6);
        }
        else
            this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public Type getType() {
        return type;
    }
    
    public String getFullName() {
        return type.toString().toLowerCase() + ":" + name;
    }
    
    @Override
    public int hashCode() {
        return getFullName().hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if(obj instanceof ExchangeModel) {
            return getFullName().equals(((ExchangeModel) obj).getFullName());
        }
        return false;
    }
    
    public void addToGraph(SWIGTYPE_p_Agraph_t graph, String processorName) {
        SWIGTYPE_p_Agnode_t inputNode = gv.node(graph, getFullName());
        gv.setv(inputNode, "id", "input-" + getFullName());
        gv.setv(inputNode, "shape", "record");
        gv.setv(inputNode, "color", "red");
        gv.setv(inputNode, "href", String.format("javascript:exchange_onclick('%s');", getFullName()));
        gv.setv(gv.edge(inputNode, processorName), "style", "dotted");
    }
}
