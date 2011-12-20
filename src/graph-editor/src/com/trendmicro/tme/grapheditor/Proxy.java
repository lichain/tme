package com.trendmicro.tme.grapheditor;

import java.net.MalformedURLException;

import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.servlets.ProxyServlet;

public class Proxy extends ProxyServlet.Transparent {
    public Proxy() {
        super("/", "0.0.0.0", 0);
    }
    
    @Override
    protected HttpURI proxyHttpURI(String scheme, String serverName, int serverPort, String uri) throws MalformedURLException {
        return new HttpURI("http://" + uri.replace("/proxy/", ""));
    }
}
