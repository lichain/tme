package com.trendmicro.tme.grapheditor;

import java.util.Map;

import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.servlet.ServletContainer;

@SuppressWarnings("serial")
public class GraphEditorContainer extends ServletContainer {
    private IocProviderFactory factory;
    
    public GraphEditorContainer(Map<Class<?>, Object> classMap) {
        factory = new IocProviderFactory(classMap);
    }
    
    @Override
    protected void initiate(ResourceConfig rc, WebApplication wa) {
        wa.initiate(rc, factory);
    }
}
