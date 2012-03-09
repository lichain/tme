package com.trendmicro.tme.grapheditor;

import java.io.FileInputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.jasper.servlet.JspServlet;
import org.eclipse.jetty.http.security.Constraint;
import org.eclipse.jetty.plus.jaas.JAASLoginService;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.tme.mfr.ExchangeFarm;

public class GraphEditorMain {
    private static final String CONFIG_PATH = System.getProperty("com.trendmicro.tme.grapheditor.conf", "/opt/trend/tme/conf/graph-editor/graph-editor.properties");
    private static final Logger logger = LoggerFactory.getLogger(GraphEditorMain.class);
    
    public static void main(String[] args) throws Exception {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(CONFIG_PATH));
            // Let the system properties override the ones in the config file
            prop.putAll(System.getProperties());
            
            String connectString = prop.getProperty("com.trendmicro.tme.grapheditor.zookeeper.quorum") + prop.getProperty("com.trendmicro.tme.grapheditor.zookeeper.tmeroot");
            ZKSessionManager.initialize(connectString, Integer.valueOf(prop.getProperty("com.trendmicro.tme.grapheditor.zookeeper.timeout")));
            ZKSessionManager.instance().waitConnected();
            
            HandlerList handlers = new HandlerList();
            
            Map<Class<?>, Object> classMap = new HashMap<Class<?>, Object>();
            classMap.put(ExchangeFarm.class, new ExchangeFarm());
            ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SECURITY | ServletContextHandler.SESSIONS);
            ServletHolder jerseyHolder = new ServletHolder(new GraphEditorContainer(classMap));
            
            handler.addServlet(new ServletHolder(new Proxy()), "/proxy/*");
            
            ServletHolder jspHolder = new ServletHolder(new JspServlet());
            jspHolder.setInitParameter("scratchdir", prop.getProperty("jasper.scratchdir", "/var/lib/tme/graph-editor/jsp"));
            jspHolder.setInitParameter("trimSpaces", "true");
            jspHolder.setInitParameter("portalhost", prop.getProperty("com.trendmicro.tme.grapheditor.portalhost", ""));
            handler.addServlet(jspHolder, "*.jsp");
            
            String webdir = prop.getProperty("com.trendmicro.tme.grapheditor.webdir");
            handler.setResourceBase(webdir);
            logger.info("Web resource base is set to {}", webdir);

            jerseyHolder.setInitParameter("com.sun.jersey.config.property.resourceConfigClass", "com.sun.jersey.api.core.PackagesResourceConfig");
            jerseyHolder.setInitParameter("com.sun.jersey.config.property.packages", "com.trendmicro.tme.grapheditor");
            handler.addServlet(jerseyHolder, "/webapp/graph-editor/*");
            
            FilterHolder loggingFilterHolder = new FilterHolder(new LoggingFilter());
            handler.addFilter(loggingFilterHolder, "/*", 1);

            boolean secure = false;
            IdentityService identityService = null;
            try {
                identityService = new ZKIdentityService("/global/acl/graph_editor_admins", ZKSessionManager.instance());
                secure = true;
            }
            catch(CODIException.NoNode e) {
                logger.warn("ACL node /global/acl/graph_editor_admins not found! Graph Editor will run in insecure mode.");
            }

            SessionHandler sessionHandler = new SessionHandler(new HashSessionManager());
            sessionHandler.getSessionManager().setIdManager(new HashSessionIdManager());
            handler.setSessionHandler(sessionHandler);

            if(secure) {
                Constraint constraint = new Constraint();
                constraint.setName(Constraint.__FORM_AUTH);
                constraint.setRoles(new String[] {
                    "user", "admin", "guest"
                });
                constraint.setAuthenticate(true);

                ConstraintMapping cm = new ConstraintMapping();
                cm.setConstraint(constraint);
                cm.setPathSpec("/webapp/graph-editor/*");

                JAASLoginService jaasLogin = new JAASLoginService("TME Graph Editor");
                jaasLogin.setLoginModuleName("ldaploginmodule");
                jaasLogin.setIdentityService(identityService);

                ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
                securityHandler.setConstraintMappings(new ConstraintMapping[] {
                    cm
                });
                securityHandler.setAuthenticator(new BasicAuthenticator());
                securityHandler.setLoginService(jaasLogin);
                securityHandler.setStrict(false);

                handler.setSecurityHandler(securityHandler);
            }

            handlers.addHandler(handler);
            
            ResourceHandler resHandler = new ResourceHandler();
            resHandler.setResourceBase(webdir);
            resHandler.setDirectoriesListed(true);
            resHandler.setWelcomeFiles(new String[]{"index.html"});
            handlers.addHandler(resHandler);

            int port = Integer.valueOf(prop.getProperty("com.trendmicro.tme.grapheditor.port"));
            Server server = new Server(port);
            server.setHandler(handlers);
            server.setSessionIdManager(handler.getSessionHandler().getSessionManager().getIdManager());
            
            server.start();
            System.err.println("Graph Editor started listening on port " + port);
            logger.info("Graph Editor started listening on port " + port);
            
            IOUtils.closeQuietly(new URL(String.format("http://127.0.0.1:%d/%s?jsp_precompile", port, "graph/graph.jsp")).openConnection().getInputStream());
            IOUtils.closeQuietly(new URL(String.format("http://127.0.0.1:%d/%s?jsp_precompile", port, "graph/index.jsp")).openConnection().getInputStream());
            IOUtils.closeQuietly(new URL(String.format("http://127.0.0.1:%d/%s?jsp_precompile", port, "processor/processor.jsp")).openConnection().getInputStream());
            IOUtils.closeQuietly(new URL(String.format("http://127.0.0.1:%d/%s?jsp_precompile", port, "processor/index.jsp")).openConnection().getInputStream());
            IOUtils.closeQuietly(new URL(String.format("http://127.0.0.1:%d/%s?jsp_precompile", port, "exchange/exchange.jsp")).openConnection().getInputStream());
            
            server.join();
        }
        catch(Exception e) {
            e.printStackTrace();
            logger.error("Graph Editor startup error: ", e);
            System.exit(-1);
        }
    }
}
