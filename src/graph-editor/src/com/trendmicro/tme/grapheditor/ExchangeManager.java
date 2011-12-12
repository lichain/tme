package com.trendmicro.tme.grapheditor;

import java.io.IOException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.xml.bind.JAXBException;

import com.sun.jersey.api.view.Viewable;
import com.trendmicro.codi.CODIException;

@Path("/exchange")
public class ExchangeManager {
    
    public ExchangeModel getExchange(@PathParam("name") String name){
        return new ExchangeModel(name);
    }
    
    @Path("/{name}")
    @GET
    @Produces("text/html")
    public Viewable getPage(@PathParam("name") String name) throws CODIException, JAXBException, IOException {
        return new Viewable("/exchange/exchange.jsp", getExchange(name));
    }

}
