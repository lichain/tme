package com.trendmicro.tme.grapheditor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.bind.JAXBException;

import org.graphviz.SWIGTYPE_p_Agraph_t;
import org.graphviz.gv;

import com.google.gson.Gson;
import com.sun.jersey.api.view.Viewable;
import com.trendmicro.codi.CODIException;
import com.trendmicro.codi.ZNode;

@Path("/graph")
public class GraphManager {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getGraphList() throws CODIException {
        return new ZNode("/global/graph/route").getChildren();
    }
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getGraphIndex() throws CODIException {
        CacheControl cc = new CacheControl();
        cc.setMustRevalidate(true);
        cc.setNoCache(true);
        cc.setNoStore(true);
        return Response.status(Status.OK).cacheControl(cc).entity(new Viewable("/graph/index.jsp", getGraphList())).build();
    }
    
    @GET
    @Produces("application/x-xdot")
    public StreamingOutput generateCombinedView(@QueryParam("graphSelected") String graphs) throws CODIException, JAXBException, IOException {
        ArrayList<GraphModel> graphList = new ArrayList<GraphModel>();
        for(String graphName : graphs.split(",")) {
            graphList.add(getGraph(graphName));
        }
        return new GraphvizStreamingOutput(generateGraph(graphList), "xdot");
    }
    
    @GET
    @Produces("image/png")
    public StreamingOutput generateCombinedViewImage(@QueryParam("graphSelected") String graphs) throws CODIException, JAXBException, IOException {
        ArrayList<GraphModel> graphList = new ArrayList<GraphModel>();
        for(String graphName : graphs.split(",")) {
            graphList.add(getGraph(graphName));
        }
        
        return new GraphvizStreamingOutput(generateGraph(graphList), "png");
    }
    
    @Path("/{name}")
    @PUT
    public void createGraph(@PathParam("name") String name) throws CODIException, JAXBException {
        ZNode node = new ZNode("/global/graph/route/" + name);
        GraphModel graph = new GraphModel(name);
        node.create(false, new Gson().toJson(graph));
    }
    
    @Path("/{name}")
    @DELETE
    public void removeGraph(@PathParam("name") String name) throws CODIException, JAXBException {
        ZNode node = new ZNode("/global/graph/route/" + name);
        node.delete();
    }
    
    private void setGraph(GraphModel graph) throws CODIException, JAXBException {
        ZNode node = new ZNode("/global/graph/route/" + graph.getName());
        node.setContent(new Gson().toJson(graph));
    }
    
    private SWIGTYPE_p_Agraph_t generateGraph(List<GraphModel> graphs) throws JAXBException, CODIException {
        SWIGTYPE_p_Agraph_t graph = gv.strictdigraph("G");
        gv.setv(graph, "rankdir", "LR");
        for(GraphModel graphModel : graphs) {
            graphModel.addToGraph(graph);
        }
        return graph;
    }
    
    @Path("/{name}")
    @GET
    @Produces("application/x-xdot")
    public StreamingOutput getXDot(@PathParam("name") String name) throws CODIException, JAXBException, IOException {
        return new GraphvizStreamingOutput(generateGraph(Arrays.asList(new GraphModel[] {
            getGraph(name)
        })), "xdot");
    }
    
    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public GraphModel getGraph(@PathParam("name") String name) throws CODIException, JAXBException {
        ZNode node = new ZNode("/global/graph/route/" + name);
        return new Gson().fromJson(node.getContentString(), GraphModel.class);
    }
    
    @Path("/{name}")
    @GET
    @Produces("text/html")
    public Viewable getPage(@PathParam("name") String name) throws CODIException, JAXBException, IOException {
        
        return new Viewable("/graph/graph.jsp", getGraph(name));
    }
    
    @Path("/{name}/rule/{rule}")
    @DELETE
    public void removeRule(@PathParam("name") String name, @PathParam("rule") String rule) throws JAXBException, CODIException {
        GraphModel graph = getGraph(name);
        graph.removeRule(rule);
        setGraph(graph);
    }
    
    @Path("/{name}/rule/{rule}")
    @PUT
    public void addRule(@PathParam("name") String name, @PathParam("rule") String rule) throws JAXBException, CODIException {
        GraphModel graph = getGraph(name);
        graph.addRule(rule);
        setGraph(graph);
    }
    
    @Path("/{name}/processor/{processor}")
    @PUT
    public void addProcessor(@PathParam("name") String name, @PathParam("processor") String processor) throws JAXBException, CODIException {
        GraphModel graph = getGraph(name);
        graph.addProcessor(processor);
        setGraph(graph);
    }
    
    @Path("/{name}/processor/{processor}")
    @DELETE
    public void removeProcessor(@PathParam("name") String name, @PathParam("processor") String processor) throws JAXBException, CODIException {
        GraphModel graph = getGraph(name);
        graph.removeProcessor(processor);
        setGraph(graph);
    }
    
    @Path("/{name}/enable")
    @PUT
    public void enable(@PathParam("name") String name) throws JAXBException, CODIException {
        GraphModel graph = getGraph(name);
        graph.setEnabled(true);
        setGraph(graph);
    }
    
    @Path("/{name}/enable")
    @DELETE
    public void disable(@PathParam("name") String name) throws JAXBException, CODIException {
        GraphModel graph = getGraph(name);
        graph.setEnabled(false);
        setGraph(graph);
    }
}
