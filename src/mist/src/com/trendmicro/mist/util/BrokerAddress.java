package com.trendmicro.mist.util;

import com.trendmicro.mist.MistException;

public class BrokerAddress {
    private String type = "";
    private ConnectionList connList = new ConnectionList();

    ////////////////////////////////////////////////////////////////////////////////

    public BrokerAddress() {
    }
    
    public void setType(String t) throws MistException {
        type = parseBrokerType(t);
    }
    
    public String getType() {
        return type;
    }

    public void setConnectionList(String address_list) {
        connList.set(address_list);
    }
    
    public void mergeConnectionList(String address_list) {
        connList.merge(address_list);
    }
    
    public void addAddress(String host_port) {
        connList.add(host_port);
    }
    
    public void reset() {
        type = "";
        connList.reset();
    }
    
    public String getAddressString() {
        return connList.toString();
    }
    
    public static String parseBrokerType(String t) throws MistException {
        if(!(t.equals("activemq") || t.equals("openmq")))
            throw new MistException(String.format("unknown broker type `%s'", t));
        return t;
    }
}
