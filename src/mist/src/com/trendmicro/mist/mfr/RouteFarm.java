package com.trendmicro.mist.mfr;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.Gson;
import com.trendmicro.codi.DataListener;
import com.trendmicro.codi.DataObserver;
import com.trendmicro.mist.util.Exchange;
import com.trendmicro.spn.common.util.Utils;

public class RouteFarm implements DataListener {
    private static Log logger = LogFactory.getLog(RouteFarm.class);
    private static RouteFarm m_theSingleton = null;

    private HashMap<String, Vector<Exchange>> routeTable = new HashMap<String, Vector<Exchange>>();
    private HashMap<String, HashMap<String, ArrayList<String>>> graphs = new HashMap<String, HashMap<String, ArrayList<String>>>();
    private DataObserver obs = null;
    private ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();
    private long lastUpdateTs = 0;

    /**
     * The constructor, which starts the data observer on the graph root node
     */
    private RouteFarm() {
        obs = new DataObserver(graphRoot + "/route", this, true, 0);
        obs.start();
    }

    // ///////////////////////////////////////////////////////////////////////

    public static final String graphRoot = "/tme2/global/graph";

    public static RouteFarm getInstance() {
        if(null == m_theSingleton)
            m_theSingleton = new RouteFarm();
        return m_theSingleton;
    }

    public void reset() {
        routeTable.clear();
        obs = new DataObserver(graphRoot + "/route" , this, true, 0);
        obs.start();
    }

    /**
     * This function is for testing only.
     * 
     * @return The whole route table
     */
    public HashMap<String, Vector<Exchange>> getRouteTable() {
        return routeTable;
    }

    /**
     * This function is for testing only.
     * 
     * @return The last update's timestamp
     */
    public long getLastUpdateTs() {
        return lastUpdateTs;
    }

    /**
     * Get a fully copied list of destination exchange names
     * 
     * @param src
     *            The exchange name where a producer will send a message to
     * @return A fully copied list of exchanges name where a message to src
     *         should be redirected to<br>
     *         An empty exchange name indicates that do nothing and drop the
     *         message.
     */
    @SuppressWarnings("unchecked")
    public List<Exchange> getDestList(String src) {
        try {
            rwlock.readLock().lock();
            Vector<Exchange> destList = routeTable.get(src);
            if(destList == null)
                return null;
            else
                // Clone the list
                return (Vector<Exchange>) (routeTable.get(src).clone());
        }
        catch(Exception e) {
            logger.error(Utils.convertStackTrace(e));
        }
        finally {
            rwlock.readLock().unlock();
        }
        return null;
    }

    /**
     * For logging only, a null destination exchange (which means to drop
     * messages) will be marked as /dev/null
     * 
     * @return A route table in string representation
     */
    public String getRouteString() {
        StringBuilder sb = new StringBuilder();
        sb.append("==Route Updated==\n");
        for(Entry<String, Vector<Exchange>> ent : routeTable.entrySet()) {
            sb.append(ent.getKey());
            sb.append(" -> ");

            for(Exchange dest : ent.getValue()) {
                sb.append(dest);
                sb.append(", ");
            }
            sb.delete(sb.length() - 2, sb.length() - 1);
            sb.append("\n");
        }
        sb.append("=================");
        return sb.toString();
    }

    /**
     * Parse the raw graph node content and update the routing table
     * 
     * @param graphName
     *            The graph's name
     * @param raw
     *            The raw content of it
     */
    private void updateRoute(String graphName, byte[] raw) {
        // Construct a new graph in map form from the raw content
        HashMap<String, ArrayList<String>> newGraph = new HashMap<String, ArrayList<String>>();
        if(raw != null) {
            if(raw.length > 0) {
                GraphModel graph = null;
                try {
                    Gson gson = new Gson();
                    graph = gson.fromJson(new String(raw), GraphModel.class);
                }
                catch(Exception e) {
                    logger.error(Utils.convertStackTrace(e));
                    return;
                }
       
                if(graph.isEnabled()) {
                    for(String rule : graph.getRules()) {
                        // Source is before the hyphen
                        String[] srcDst = rule.split("-");
                        String src = new String(srcDst[0]);
                        
                        // If there is nothing after the hyphen, then the dest is an empty string
                        String dest;
                        if(srcDst.length == 1)
                            dest = new String("");
                        else
                            dest = new String(srcDst[1]);
                        
                        // Put the destinations in the graph
                        ArrayList<String> destList = newGraph.get(src);
                        if(destList == null) {
                            destList = new ArrayList<String>();
                            newGraph.put(src, destList);
                        }
                        destList.add(dest);
                    }
                }
            }
        }

        // Get the old graph stored locally
        HashMap<String, ArrayList<String>> oldGraph = graphs.remove(graphName);
        if(oldGraph == null)
            oldGraph = new HashMap<String, ArrayList<String>>();

        // Lock the routing table and update it
        rwlock.writeLock().lock();
        // Remove rules do not exist in the new graph anymore
        for(Entry<String, ArrayList<String>> ent : oldGraph.entrySet()) {
            String src = ent.getKey();
            ArrayList<String> deletedDestList = new ArrayList<String>(oldGraph.get(src));
            if(newGraph.containsKey(src))
                deletedDestList.removeAll(newGraph.get(src));

            Vector<Exchange> routeDestList = routeTable.get(src);
            for(String dest : deletedDestList)
                routeDestList.remove(new Exchange(dest));

            if(routeDestList.size() == 0)
                routeTable.remove(src);
        }
        // Add new rules from the new graph
        for(Entry<String, ArrayList<String>> ent : newGraph.entrySet()) {
            String src = ent.getKey();
            ArrayList<String> newDestList = new ArrayList<String>(newGraph.get(src));
            if(oldGraph.containsKey(src))
                newDestList.removeAll(oldGraph.get(src));

            Vector<Exchange> routeDestList = routeTable.get(src);
            if(routeDestList == null) {
                routeDestList = new Vector<Exchange>();
                routeTable.put(src, routeDestList);
            }
            for(String dest : newDestList) {
                routeDestList.add(new Exchange(dest));
            }
        }
        rwlock.writeLock().unlock();

        // Store the new graph
        if(!newGraph.isEmpty())
            graphs.put(graphName, newGraph);
        logger.info("\n" + getRouteString());
    }

    @Override
    public void onDataChanged(String parentPath, Map<String, byte[]> changeMap) {
        for(Entry<String, byte[]> ent : changeMap.entrySet()) {
            // Ignore the graph root node event
            if(ent.getKey().length() == 0)
                continue;
            // Use updateRoute to update the routing table
            updateRoute(ent.getKey(), ent.getValue());
        }
        lastUpdateTs = new Date().getTime();
    }
}
