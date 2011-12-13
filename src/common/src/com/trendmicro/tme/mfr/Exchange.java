package com.trendmicro.tme.mfr;

public class Exchange {
    private boolean isQueue = true;
    private String name = "";
    private String broker = "";

    // /////////////////////////////////////////////////////////////////////////////////////////
    // isValidExchange supportly for openmq use only
    public static boolean isValidExchange(String name) {
        if(name == null || name.length() == 0)
            return false;
        if(name.startsWith("mq"))
            return false;
        if(name.contains("-") || name.contains("*") || name.contains(">"))
            return false;
        char c = name.charAt(0);
        if(!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c == '_') || (c == '$')))
            return false;
        return true;
    }

    public Exchange() {
    }

    public Exchange(String ex_name) {
        set(ex_name);
    }

    public void set(String ex_name) {
        if(ex_name.startsWith("queue:")) {
            isQueue = true;
            name = ex_name.substring(6);
        }
        else if(ex_name.startsWith("topic:")) {
            isQueue = false;
            name = ex_name.substring(6);
        }
        else
            name = ex_name;
    }

    @Override
    public boolean equals(Object obj) {
        return (((Exchange) obj).isQueue() == isQueue) && (((Exchange) obj).getName().equals(name));
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    public void setBroker(String b) {
        broker = b;
    }

    public String getBroker() {
        return broker;
    }

    public String getName() {
        return name;
    }

    public boolean isQueue() {
        return isQueue;
    }

    public void setQueue() {
        isQueue = true;
    }

    public void setTopic() {
        isQueue = false;
    }

    public String toString() {
        return String.format("%s:%s", isQueue ? "queue": "topic", name);
    }

    public void reset() {
        name = "";
        isQueue = true;
    }
}
