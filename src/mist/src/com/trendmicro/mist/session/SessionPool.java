package com.trendmicro.mist.session;

import java.io.StringWriter;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.nocrala.tools.texttablefmt.CellStyle;
import org.nocrala.tools.texttablefmt.CellStyle.HorizontalAlign;
import org.nocrala.tools.texttablefmt.Table;

import com.trendmicro.mist.Client;
import com.trendmicro.mist.Connection;
import com.trendmicro.mist.Daemon;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.proto.GateTalk;

public class SessionPool {
    public static TreeMap<Integer, Session> pool = new TreeMap<Integer, Session>();

    /**
     * Get a session from the session pool by session id. If the session's role
     * is not specified yet, then create the concrete session according to the
     * role. It is thread-safe.
     * 
     * @param sessId
     *            The session id
     * @param role
     *            The request role, GateTalk.Request.Role.SINK or
     *            GateTalk.Request.Role.SOURCE<br>
     *            If role is null, then if the session has not been initialized,
     *            it will return null
     * @return The ConsumerSession / ProducerSession in the session pool
     * @throws MistException
     *             If the session id is not valid, throw a MistException
     */
    public synchronized static Session getOrCreateConcreteSession(int sessId, GateTalk.Request.Role role) throws MistException {
        if(!pool.containsKey(sessId))
            throw new MistException("invalid session id " + sessId);
        Session sess = SessionPool.pool.get(sessId);
        if(sess == null && role != null) {
            if(role == GateTalk.Request.Role.SOURCE)
                sess = new ConsumerSession(sessId, null);
            else if(role == GateTalk.Request.Role.SINK)
                sess = new ProducerSession(sessId, null);
            SessionPool.pool.put(sessId, sess);
        }
        return sess;
    }

    public static String getSessionListString() {
        StringWriter strOut = new StringWriter();

        strOut.write(String.format("%d sessions%n", pool.size()));
        if(pool.size() > 0) {
            Table tab = new Table(5);
            tab.addCell("ID");
            tab.addCell("Status");
            tab.addCell("Type");
            tab.addCell("Exchange");
            tab.addCell("Conn. IDs");

            try {
                for(Entry<Integer, Session> ent : pool.entrySet()) {
                    if(ent.getValue() == null) {
                        tab.addCell(ent.getKey().toString());
                        tab.addCell("idle");
                        tab.addCell("");
                        tab.addCell("");
                        tab.addCell("");
                    }
                    else {
                        Collection<Client> clients = ent.getValue().getClientList();
                        Session sess = ent.getValue();
                        tab.addCell(ent.getKey().toString());
                        tab.addCell(sess.isAttached() ? "BUSY": "idle");
                        if(sess instanceof ProducerSession)
                            tab.addCell("producer");
                        else
                            tab.addCell("consumer");
                        String exchanges = "";
                        String conn_ids = "";

                        if(ent.getValue().isAttached())
                            for(Client c : clients)
                                conn_ids += (c.getConnection().getId() + " ");
                        for(Client c : clients)
                            exchanges += (c.getExchange().toString() + " ");
                        tab.addCell(exchanges);
                        tab.addCell(conn_ids);
                    }
                }
            }
            catch(Exception e) {
            }
            strOut.write(tab.render() + "\n");
        }

        strOut.write(String.format("%d connections%n", Daemon.connectionPool.size()));
        if(Daemon.connectionPool.size() > 0) {
            Table tab = new Table(6);
            tab.addCell("ID");
            tab.addCell("Connected");
            tab.addCell("Type");
            tab.addCell("Auth");
            tab.addCell("Host");
            tab.addCell("Ref. Count");
            for(Connection conn : Daemon.connectionPool) {
                tab.addCell(String.valueOf(conn.getId()));
                tab.addCell(String.valueOf(conn.isConnected()), new CellStyle(HorizontalAlign.center));
                tab.addCell(conn.getType());
                tab.addCell(conn.getConfig().getUsername() + ":*");
                tab.addCell(conn.getConnectionString());
                tab.addCell(String.valueOf(conn.getReferenceCount()), new CellStyle(HorizontalAlign.right));
            }
            strOut.write(tab.render() + "\n");
        }

        return strOut.toString();
    }
}
