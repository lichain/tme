package com.trendmicro.tme.grapheditor;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.jersey.api.json.JSONJAXBContext;

@SuppressWarnings("rawtypes")
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JsonListWriter implements MessageBodyWriter<List> {
    @Override
    public boolean isWriteable(Class<?> type, Type arg1, Annotation[] arg2, MediaType arg3) {
        return List.class.isAssignableFrom(type);
    }
    
    @Override
    public long getSize(List arg0, Class<?> arg1, Type arg2, Annotation[] arg3, MediaType arg4) {
        return -1;
    }
    
    @Override
    public void writeTo(List objs, Class<?> arg1, Type arg2, Annotation[] arg3, MediaType arg4, MultivaluedMap<String, Object> arg5, OutputStream out) throws IOException, WebApplicationException {
        JsonArray array = new JsonArray();
        for(Object obj : objs) {
            JsonParser parser = new JsonParser();
            try {
                JAXBContext context = JAXBContext.newInstance(obj.getClass());
                Marshaller marshaller = context.createMarshaller();
                StringWriter sw = new StringWriter();
                JSONJAXBContext.getJSONMarshaller(marshaller).marshallToJSON(obj, sw);
                array.add(parser.parse(sw.toString()));
            }
            catch(JAXBException e) {
                array.add(new JsonPrimitive(obj.toString()));
            }
        }
        out.write(new Gson().toJson(array).getBytes());
    }
}
