package com.trendmicro.tme.grapheditor;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
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
import com.trendmicro.tme.grapheditor.ProcessorModel.RenderView;

@Path("/processor")
public class ProcessorManager {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getProcessorList() throws CODIException {
        return new ZNode("/global/graph/processor").getChildren();
    }
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getProcessorIndex() throws CODIException {
        CacheControl cc = new CacheControl();
        cc.setMustRevalidate(true);
        cc.setNoCache(true);
        cc.setNoStore(true);
        return Response.status(Status.OK).cacheControl(cc).entity(new Viewable("/processor/index.jsp", getProcessorList())).build();
    }
    
    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ProcessorModel getProcessor(@PathParam("name") String name) throws JAXBException, CODIException {
        ZNode node = new ZNode("/global/graph/processor/" + name);
        return new Gson().fromJson(node.getContentString(), ProcessorModel.class);
    }
    
    @Path("/{name}")
    @PUT
    public void createProcessor(@PathParam("name") String name) throws CODIException, JAXBException {
        ZNode node = new ZNode("/global/graph/processor/" + name);
        ProcessorModel processor = new ProcessorModel(name);
        processor.addInput(name + ".in");
        processor.addOutput(name + ".out");
        node.create(false, new Gson().toJson(processor));
    }
    
    @Path("/{name}")
    @DELETE
    public void removeProcessor(@PathParam("name") String name) throws CODIException, JAXBException {
        ZNode node = new ZNode("/global/graph/processor/" + name);
        node.delete();
    }
    
    private void setProcessor(ProcessorModel processor) throws CODIException, JAXBException {
        ZNode node = new ZNode("/global/graph/processor/" + processor.getName());
        node.setContent(new Gson().toJson(processor));
    }
    
    private SWIGTYPE_p_Agraph_t generateGraph(ProcessorModel processor) {
        SWIGTYPE_p_Agraph_t graph = gv.strictdigraph("G_" + processor.getName());
        gv.setv(graph, "rankdir", "LR");
        processor.addToGraph(graph, RenderView.PROCESSOR_EDITOR);
        return graph;
    }
    
    @Path("/{name}")
    @GET
    @Produces("application/x-xdot")
    public StreamingOutput getXDot(@PathParam("name") String name) throws CODIException, JAXBException, IOException {
        return new GraphvizStreamingOutput(generateGraph(getProcessor(name)), "xdot");
    }
    
    @Path("/{name}")
    @GET
    @Produces("text/html")
    public Viewable getPage(@PathParam("name") String name) throws CODIException, JAXBException, IOException {
        return new Viewable("/processor/processor.jsp", getProcessor(name));
    }
    
    @Path("/{name}/output/{output}")
    @DELETE
    public void removeOutput(@PathParam("name") String name, @PathParam("output") String output) throws JAXBException, CODIException {
        ProcessorModel processor = getProcessor(name);
        processor.removeOutput(output);
        setProcessor(processor);
    }
    
    @Path("/{name}/output/{output}")
    @PUT
    public void addOutput(@PathParam("name") String name, @PathParam("output") String output) throws JAXBException, CODIException {
        ProcessorModel processor = getProcessor(name);
        processor.addOutput(output);
        setProcessor(processor);
    }
    
    @Path("/{name}/input/{input}")
    @DELETE
    public void removeInput(@PathParam("name") String name, @PathParam("input") String input) throws JAXBException, CODIException {
        ProcessorModel processor = getProcessor(name);
        processor.removeInput(input);
        setProcessor(processor);
    }
    
    @Path("/{name}/input/{input}")
    @PUT
    public void addInput(@PathParam("name") String name, @PathParam("input") String input) throws JAXBException, CODIException {
        ProcessorModel processor = getProcessor(name);
        processor.addInput(input);
        setProcessor(processor);
    }
}
