package com.trendmicro.tme.grapheditor;

import java.io.FileInputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.jasper.servlet.JspServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.spi.container.servlet.ServletContainer;
import com.trendmicro.codi.ZKSessionManager;

public class GraphEditor {
    private static final String CONFIG_PATH = System.getProperty("com.trendmicro.tme.grapheditor.conf", "/opt/trend/tme/conf/graph-editor.properties");
    private static final Logger logger = LoggerFactory.getLogger(GraphEditor.class);
    
    static {
        System.loadLibrary("gv_java");
    }
    
    public static void main(String[] args) throws Exception {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(CONFIG_PATH));
            // Let the system properties override the ones in the config file
            prop.putAll(System.getProperties());
            
            ZKSessionManager.initialize(prop.getProperty("com.trendmicro.tme.grapheditor.zookeeper.quorum"), Integer.valueOf(prop.getProperty("com.trendmicro.tme.grapheditor.zookeeper.timeout")));
            ZKSessionManager.instance().waitConnected();
            
            HandlerList handlers = new HandlerList();
            
            ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SECURITY | ServletContextHandler.SESSIONS);
            ServletHolder jerseyHolder = new ServletHolder(new ServletContainer());
            
            handler.addServlet(new ServletHolder(new DefaultServlet()), "/static/*");
            handler.addServlet(new ServletHolder(new JspServlet()), "*.jsp");
            handler.setResourceBase(prop.getProperty("com.trendmicro.tme.grapheditor.webdir"));
            logger.info("Web resource base is set to {}", prop.getProperty("com.trendmicro.tme.grapheditor.webdir"));
            
            jerseyHolder.setInitParameter("com.sun.jersey.config.property.resourceConfigClass", "com.sun.jersey.api.core.PackagesResourceConfig");
            jerseyHolder.setInitParameter("com.sun.jersey.config.property.packages", "com.trendmicro.tme.grapheditor");
            handler.addServlet(jerseyHolder, "/webapp/graph-editor/*");
            
            FilterHolder loggingFilterHolder = new FilterHolder(new LoggingFilter());
            loggingFilterHolder.setInitParameter("name", "Brain");
            handler.addFilter(loggingFilterHolder, "/*", 1);
            
            handlers.addHandler(handler);
            
            int port = Integer.valueOf(prop.getProperty("com.trendmicro.tme.grapheditor.port"));
            Server server = new Server(port);
            server.setHandler(handlers);
            
            server.start();
            System.err.println("Graph Editor started listening on port " + port);
            logger.info("Graph Editor started listening on port " + port);
            
            IOUtils.closeQuietly(new URL(String.format("http://localhost:%d/%s?jsp_precompile", port, "graph/graph.jsp")).openConnection().getInputStream());
            IOUtils.closeQuietly(new URL(String.format("http://localhost:%d/%s?jsp_precompile", port, "graph/index.jsp")).openConnection().getInputStream());
            IOUtils.closeQuietly(new URL(String.format("http://localhost:%d/%s?jsp_precompile", port, "processor/processor.jsp")).openConnection().getInputStream());
            IOUtils.closeQuietly(new URL(String.format("http://localhost:%d/%s?jsp_precompile", port, "processor/index.jsp")).openConnection().getInputStream());
            
            server.join();
        }
        catch(Exception e) {
            e.printStackTrace();
            logger.error("Graph Editor startup error: ", e);
            System.exit(-1);
        }
    }
}
