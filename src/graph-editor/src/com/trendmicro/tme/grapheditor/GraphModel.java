package com.trendmicro.tme.grapheditor;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
    private Set<String> admins = new HashSet<String>();

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

    public Set<String> getAdmins() {
        return admins;
    }

    public void addAdmin(String admin) {
        admins.add(admin);
    }

    public void removeAdmin(String admin) {
        admins.remove(admin);
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

    public String toSubgraph() throws JsonSyntaxException, CODIException {
        StringBuilder sb = new StringBuilder();

        for(String processorName : processors) {
            ZNode node = new ZNode("/global/graph/processor/" + processorName);
            ProcessorModel processor = new Gson().fromJson(node.getContentString(), ProcessorModel.class);
            sb.append(processor.toSubgraph(RenderView.GRAPH_EDITOR));
        }

        for(String rule : rules) {
            String[] tmpArray = rule.split("-");
            String src = tmpArray[0];
            String dst = tmpArray[1];
            sb.append(String.format("\"%s\" -> \"%s\" [style=%s label=x href=\"%s\"];\n", src, dst, enabled ? "solid": "dashed", String.format("javascript:remove_rule('%s');", rule.replaceAll("\\\"", "\\\\\""))));
        }

        return sb.toString();
    }
}
