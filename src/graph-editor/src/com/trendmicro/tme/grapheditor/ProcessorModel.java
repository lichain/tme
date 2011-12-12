package com.trendmicro.tme.grapheditor;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.graphviz.SWIGTYPE_p_Agedge_t;
import org.graphviz.SWIGTYPE_p_Agnode_t;
import org.graphviz.SWIGTYPE_p_Agraph_t;
import org.graphviz.gv;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
public class ProcessorModel {
    public static enum RenderView {
        PROCESSOR_EDITOR, GRAPH_EDITOR
    }
    
    private String name;
    private Set<ExchangeModel> inputs = new HashSet<ExchangeModel>();
    private Set<String> outputs = new HashSet<String>();
    
    public ProcessorModel() {
    }
    
    public ProcessorModel(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public Set<ExchangeModel> getInputs() {
        return inputs;
    }
    
    public Set<String> getOutputs() {
        return outputs;
    }
    
    public void addInput(String input) {
        inputs.add(new ExchangeModel(input));
    }
    
    public void removeInput(String input) {
        inputs.remove(new ExchangeModel(input));
    }
    
    public void addOutput(String output) {
        outputs.add(output);
    }
    
    public void removeOutput(String output) {
        outputs.remove(output);
    }
    
    public void addToGraph(SWIGTYPE_p_Agraph_t graph, RenderView view) {
        SWIGTYPE_p_Agraph_t subGraph = gv.graph(graph, "cluster_" + name);
        gv.setv(subGraph, "style", "filled");
        gv.setv(subGraph, "color", "#F0F9E8");
        gv.setv(subGraph, "nodesep", "0.2");
        gv.setv(subGraph, "ranksep", "0.2");
        
        for(ExchangeModel input : inputs) {
            input.addToGraph(subGraph, name);
        }
        
        if(view.equals(RenderView.PROCESSOR_EDITOR)) {
            SWIGTYPE_p_Agnode_t addInputNode = gv.node(subGraph, "add_input_" + name);
            gv.setv(addInputNode, "label", "+");
            gv.setv(addInputNode, "shape", "doublecircle");
            gv.setv(addInputNode, "href", "javascript:add_input();");
            
            SWIGTYPE_p_Agedge_t addInputLink = gv.edge(addInputNode, name);
            gv.setv(addInputLink, "dir", "none");
            gv.setv(addInputLink, "style", "invisible");
        }
        
        SWIGTYPE_p_Agnode_t processorNode = gv.node(subGraph, name);
        gv.setv(processorNode, "shape", "box3d");
        if(view.equals(RenderView.GRAPH_EDITOR)) {
            gv.setv(processorNode, "href", String.format("javascript:processor_onclick('%s');", name));
        }
        
        if(view.equals(RenderView.PROCESSOR_EDITOR)) {
            SWIGTYPE_p_Agnode_t addOutputNode = gv.node(subGraph, "add_output_" + name);
            gv.setv(addOutputNode, "label", "+");
            gv.setv(addOutputNode, "shape", "doublecircle");
            gv.setv(addOutputNode, "href", "javascript:add_output();");
            
            SWIGTYPE_p_Agedge_t addOutputLink = gv.edge(processorNode, addOutputNode);
            gv.setv(addOutputLink, "dir", "none");
            gv.setv(addOutputLink, "style", "invisible");
        }
        
        for(String output : outputs) {
            SWIGTYPE_p_Agnode_t outputNode = gv.node(subGraph, output);
            gv.setv(outputNode, "id", "output-" + output);
            gv.setv(outputNode, "shape", "record");
            gv.setv(outputNode, "color", "green");
            gv.setv(outputNode, "href", view.equals(RenderView.PROCESSOR_EDITOR) ? String.format("javascript:remove_output('%s');", output): "void(0);");
            gv.setv(gv.edge(processorNode, outputNode), "style", "dotted");
        }
    }
}
