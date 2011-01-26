package com.trendmicro.mist.util;

import java.util.ArrayList;
import org.apache.commons.lang.StringUtils;

public class ConnectionList {
    private ArrayList<Address> addressList = new ArrayList<Address>();
    
    public ConnectionList() {
    }

    public ConnectionList(String address_list) {
        set(address_list);
    }

    public void reset() {
        addressList.clear();
    }

    public void add(String host_port) {
        add(new Address(host_port));
    }
    
    public void add(Address addr) {
        addressList.add(addr);
    }
    
    public int size() {
        return addressList.size();
    }
    
    public Address get(int idx) {
        return addressList.get(idx);
    }

    public void merge(String address_list) {
        String [] v = address_list.trim().split(",");
        for(String s: v)
            add(s);
    }

    public void merge(String host_list, String port_list) {
        String [] hosts = host_list.split(",");
        String [] ports = port_list.split(",");
        if(hosts.length == ports.length) {
            for(int i = 0; i < hosts.length; i++)
                add(hosts[i] + ":" + ports[i]);
        }
    }

    public void set(String address_list) {
        reset();
        merge(address_list);
    }

    public void set(String host_list, String port_list) {
        reset();
        merge(host_list, port_list);
    }

    public String toString() {
        return StringUtils.join(addressList, ",");
    }
    
    public String getHosts() {
        String [] h = new String[size()];
        for(int i = 0; i < addressList.size(); i++)
            h[i] = addressList.get(i).getHost();
        return StringUtils.join(h, ",");
    }
    
    public String getPorts() {
        String [] p = new String[size()];
        for(int i = 0; i < addressList.size(); i++)
            p[i] = addressList.get(i).getPort();
        return StringUtils.join(p, ",");
    }
}
