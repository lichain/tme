package com.trendmicro.mist.util;

public class Address {
    private String host = "";
    private String port = "";

    ////////////////////////////////////////////////////////////////////////////////

    public Address() {
    }

    public Address(String host_port) {
        set(host_port);
    }
    
    public void set(String host_port) {
        String [] v = host_port.trim().split(":"); 
        if(v.length > 0) {
            reset();
            host = (v[0] == null ? "": v[0]);
            if(v.length > 1)
                port = (v[1] == null ? "": v[1]);
        }
    }
    
    public void reset() {
        host = "";
        port = "";
    }
    
    public String getHost() {
        return host;
    }
    
    public String getPort() {
        return port;
    }
    
    public String toString() {
        return String.format("%s%s%s", host, (port.equals("") ? "": ":"), port);
    }
}
