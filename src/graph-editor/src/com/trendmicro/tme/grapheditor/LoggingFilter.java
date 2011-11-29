package com.trendmicro.tme.grapheditor;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);
    
    static class FilterResponse extends HttpServletResponseWrapper {
        private int status;
        private String contentType;
        
        public FilterResponse(HttpServletResponse response) {
            super(response);
        }
        
        @Override
        public void addHeader(String name, String value) {
            if(name.equals("Content-Type")) {
                contentType = value;
            }
            super.addHeader(name, value);
        }
        
        public String getContentType() {
            return contentType;
        }
        
        @Override
        public void setStatus(int sc) {
            status = sc;
            super.setStatus(sc);
        }
        
        @Override
        public void sendError(int sc, String msg) throws IOException {
            status = sc;
            super.sendError(sc, msg);
        }
        
        @Override
        public void sendError(int sc) throws IOException {
            status = sc;
            super.sendError(sc);
        }
        
        @Override
        public void setStatus(int sc, String sm) {
            status = sc;
            super.setStatus(sc, sm);
        }
        
        public int getStatus() {
            return status;
        }
    }
    
    private String name = "";
    
    @Override
    public void destroy() {
    }
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        if(req instanceof HttpServletRequest && res instanceof HttpServletResponse) {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;
            FilterResponse fRes = new FilterResponse(response);
            
            logger.info(String.format("%s:%s [%s %s%s Accept:(%s) ] ", name, request.getUserPrincipal() == null ? "": request.getUserPrincipal().getName(), request.getMethod(), request.getRequestURI(), request.getQueryString() == null ? "": " QS:" + request.getQueryString(), request.getHeader("Accept")));
            chain.doFilter(req, fRes);
            logger.info(String.format("%s: [/%s/%s]", name, Status.fromStatusCode(fRes.getStatus()), fRes.getContentType() == null ? "": " Content-Type:" + fRes.getContentType()));
        }
    }
    
    @Override
    public void init(FilterConfig config) throws ServletException {
        name = config.getInitParameter("name");
    }
}
