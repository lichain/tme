package com.trendmicro.tme.grapheditor;

import java.security.Principal;

import javax.security.auth.Subject;

import org.eclipse.jetty.security.DefaultUserIdentity;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.RunAsToken;
import org.eclipse.jetty.server.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZKSessionManager;
import com.trendmicro.codi.ZNode;

public class ZKIdentityService implements IdentityService {
    private final static Logger logger = LoggerFactory.getLogger(ZKIdentityService.class);
    private ZNode authNode = null;
    private static final String[] guestRole = new String[] {
        "guest"
    };

    /**
     * The constructor of ZkIdentityService The Authorization node stored on
     * Zookeeper should be the format<br>
     * 
     * <pre>
     * [user1] [role1],[role2]
     * [user2] [role1]
     * ...
     * </pre>
     * 
     * @param authNodePath
     *            The authorization node's path on Zookeeper
     * @param zksm
     *            A initialized Zookeeper session, which has the privilege to
     *            read the authorization node
     * @throws InterruptedException
     * @throws CODIException
     *             If the node does not exist, then the CODIException is
     *             instance of CODIException.NoNode
     */
    public ZKIdentityService(String authNodePath, ZKSessionManager zksm) throws InterruptedException, CODIException {
        if(authNodePath == null)
            throw new IllegalArgumentException("The authorization node path should not be null");
        if(zksm == null)
            throw new IllegalStateException("The ZKSessionManager should be initialized");
        authNode = new ZNode(authNodePath, zksm);
        authNode.getContent();
    }

    @Override
    public Object associate(UserIdentity arg0) {
        return new Object();
    }

    @Override
    public void disassociate(Object arg0) {
    }

    @Override
    public UserIdentity getSystemUserIdentity() {
        return null;
    }

    @Override
    public RunAsToken newRunAsToken(String arg0) {
        return null;
    }

    @Override
    public UserIdentity newUserIdentity(Subject subject, Principal principal, String[] roles) {
        /**
         * Parse the auth node's content, and assign user's roles when the id
         * matches
         */
        String nodeRaw = null;
        try {
            nodeRaw = new String(authNode.getContent());
        }
        catch(Exception e) {
            logger.warn(e.getMessage(), e);
        }

        if(nodeRaw == null)
            return null;

        for(String line : nodeRaw.split("\n")) {
            String[] userRolePair = line.split(" ");
            if(userRolePair.length != 2) {
                continue;
            }
            String user = userRolePair[0];

            String[] userRoles = userRolePair[1].split(",");
            if(principal.getName().equals(user)) {
                return new DefaultUserIdentity(subject, principal, userRoles);
            }
        }
        return new DefaultUserIdentity(subject, principal, guestRole);
    }

    @Override
    public Object setRunAs(UserIdentity arg0, RunAsToken arg1) {
        return null;
    }

    @Override
    public void unsetRunAs(Object arg0) {
    }
}
