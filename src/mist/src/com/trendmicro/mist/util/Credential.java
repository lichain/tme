package com.trendmicro.mist.util;
import org.apache.commons.lang.StringUtils;

public class Credential {
    private String user = "";
    private String password = "";

    ////////////////////////////////////////////////////////////////////////////////

    public Credential() {
    }

    public Credential(String auth) {
        set(auth);
    }
    
    public void set(String auth) {
        String [] v = auth.trim().split(":"); 
        if(v.length > 0) {
            reset();
            user = (v[0] == null ? "": v[0]);
            if(v.length > 1)
                password = (v[1] == null ? "": v[1]);
        }
    }
    
    public void reset() {
        user = "";
        password = "";
    }
    
    public String getUser() {
        return user;
    }
    
    public String getPassword() {
        return password;
    }
    
    public String toString() {
        return StringUtils.join(new String [] { user, password }, ":");
    }
}
