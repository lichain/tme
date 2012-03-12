package com.trendmicro.tme.grapheditor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphvizStreamingOutput implements StreamingOutput {
    private static final Logger logger = LoggerFactory.getLogger(GraphvizStreamingOutput.class);
    private String dot;
    private String type;

    public GraphvizStreamingOutput(String dot, String type) {
        this.dot = dot;
        this.type = type;
    }

    @Override
    public void write(final OutputStream outputStream) throws IOException, WebApplicationException {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(String.format("dot -T%s", type).split(" "));
            final InputStream resultStream = p.getInputStream();
            final InputStream errorStream = p.getErrorStream();
            final OutputStream toDotStream = p.getOutputStream();

            new Thread() {
                @Override
                public void run() {
                    try {
                        toDotStream.write(dot.getBytes());
                        toDotStream.close();
                    }
                    catch(IOException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }.start();

            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            new Thread() {
                @Override
                public void run() {
                    try {
                        IOUtils.copy(errorStream, bos);
                    }
                    catch(IOException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }.start();

            IOUtils.copy(resultStream, outputStream);
            if(p.waitFor() != 0) {
                String errorMsg = bos.toString();
                logger.error("dot render error: {}", errorMsg);
                logger.error("\n======== input to dot =========\n{}\n===============================", dot);
                throw new Exception("dot render error: " + errorMsg);
            }
        }
        catch(Exception e) {
            logger.error(e.getMessage(), e);
            throw new WebApplicationException(e, Status.INTERNAL_SERVER_ERROR.getStatusCode());
        }
        finally {
            p.destroy();
        }
    }
}
