package com.trendmicro.tme.grapheditor;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.graphviz.SWIGTYPE_p_Agedge_t;
import org.graphviz.SWIGTYPE_p_Agraph_t;
import org.graphviz.gv;

import com.google.gson.Gson;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZNode;
import com.trendmicro.tme.grapheditor.ProcessorModel.RenderView;

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
    
    public void addToGraph(SWIGTYPE_p_Agraph_t graph) throws JAXBException, CODIException {
        for(String processorName : processors) {
            ZNode node = new ZNode("/global/graph/processor/" + processorName);
            ProcessorModel processor = new Gson().fromJson(node.getContentString(), ProcessorModel.class);
            processor.addToGraph(graph, RenderView.GRAPH_EDITOR);
        }
        for(String rule : rules) {
            String[] tmpArray = rule.split("-");
            String src = tmpArray[0];
            String dst = tmpArray[1];
            
            SWIGTYPE_p_Agedge_t edge = gv.edge(graph, src, dst);
            gv.setv(edge, "style", enabled ? "solid": "dashed");
            gv.setv(edge, "label", "x");
            gv.setv(edge, "href", String.format("javascript:remove_rule('%s');", rule.replaceAll("\\\"", "\\\\\"")));
        }
    }
}
