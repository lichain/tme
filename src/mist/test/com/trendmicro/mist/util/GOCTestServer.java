package com.trendmicro.mist.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class GOCTestServer implements Closeable, HttpHandler {
    private HttpServer server;
    private HashMap<String, byte[]> storage = new HashMap<String, byte[]>();
    private int cnt = 0;

    public GOCTestServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this);
        server.start();
    }

    @Override
    public void close() throws IOException {
        server.stop(0);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if(ex.getRequestMethod().compareTo("POST") == 0) {
            cnt++;
            int length = Integer.valueOf(ex.getRequestHeaders().get("content-length").get(0));
            byte[] buf = new byte[length];
            ex.getRequestBody().read(buf);

            String url = "http://localhost:" + server.getAddress().getPort() + "/depot/" + cnt;
            storage.put("/depot/" + cnt, buf);

            ArrayList<String> locList = new ArrayList<String>();
            locList.add(url);
            ex.getResponseHeaders().put("Location", locList);
            ex.sendResponseHeaders(HttpURLConnection.HTTP_CREATED, 0);
            ex.close();
        }
        else {
            byte[] obj = storage.get(ex.getRequestURI().toString());
            if(obj != null) {
                ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, obj.length);
                ex.getResponseBody().write(obj);
            }
            ex.close();
        }
    }
}
