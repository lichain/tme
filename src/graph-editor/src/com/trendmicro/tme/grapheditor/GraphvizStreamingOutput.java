package com.trendmicro.tme.grapheditor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.graphviz.SWIGTYPE_p_Agraph_t;
import org.graphviz.gv;

public class GraphvizStreamingOutput implements StreamingOutput {
    private SWIGTYPE_p_Agraph_t graph;
    private String type;
    
    public GraphvizStreamingOutput(SWIGTYPE_p_Agraph_t graph, String type) {
        this.graph = graph;
        this.type = type;
    }
    
    @Override
    public void write(OutputStream outputStream) throws IOException, WebApplicationException {
        gv.layout(graph, "dot");
        File tmp = File.createTempFile("graph-editor", null);
        try {
            if(!gv.render(graph, type, tmp.getAbsolutePath())) {
                throw new WebApplicationException(new Exception("GraphViz Render Error!"), Status.INTERNAL_SERVER_ERROR.getStatusCode());
            }
            IOUtils.copy(new FileInputStream(tmp.getAbsolutePath()), outputStream);
        }
        finally {
            tmp.delete();
        }
    }
}
