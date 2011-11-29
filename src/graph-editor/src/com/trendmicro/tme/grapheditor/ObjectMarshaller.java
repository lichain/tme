package com.trendmicro.tme.grapheditor;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import com.sun.jersey.api.json.JSONJAXBContext;

public final class ObjectMarshaller {
    public static String marshallToXml(Object obj) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(obj.getClass());
        Marshaller marshaller = context.createMarshaller();
        StringWriter sw = new StringWriter();
        marshaller.marshal(obj, sw);
        return sw.toString();
    }       

    public static Object unmarshallFromXml(Class<?> objClass, String xml) throws JAXBException {        
        JAXBContext context = JAXBContext.newInstance(objClass);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return unmarshaller.unmarshal(new StreamSource(new StringReader(xml)));
    }
    
    public static String marshallToJson(Object obj) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(obj.getClass());
        Marshaller marshaller = context.createMarshaller();
        StringWriter sw = new StringWriter();
        JSONJAXBContext.getJSONMarshaller(marshaller).marshallToJSON(obj, sw);
        return sw.toString();
    }
    
    public static <T>T unmarshallFromJson(Class<T> objClass, String json) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(objClass);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return JSONJAXBContext.getJSONUnmarshaller(unmarshaller).unmarshalFromJSON(new StringReader(json), objClass);
    }
}
