package com.trendmicro.tme.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;

public final class GOCUtils {
    private HttpClient m_http_client;

    public GOCUtils() {
        m_http_client = new DefaultHttpClient();
    }

    public byte[] copyToArray(InputStream is) throws IOException {
        try {
            ByteArrayOutputStream builder = new ByteArrayOutputStream(4096);
            byte[] buffer = new byte[1024];
            int len = 0;

            while((len = is.read(buffer)) != -1) {
                if(len > 0)
                    builder.write(buffer, 0, len);
            }
            return builder.toByteArray();
        }
        finally {
            is.close();
        }
    }

    public synchronized byte[] get(URI uri) throws IOException {
        HttpGet httpget = new HttpGet(uri);

        // Execute the request
        HttpResponse response = m_http_client.execute(httpget);

        int status = response.getStatusLine().getStatusCode();
        if(status != 200) {
            throw new IOException("Failed to download uri: " + uri + " status: " + status);
        }

        // If the response does not enclose an entity, there is no need
        // to worry about connection release
        HttpEntity entity = response.getEntity();
        if(entity != null) {
            byte[] result = copyToArray(entity.getContent());
            return result;
        }
        return null;
    }

    public synchronized String post(URI uri, byte[] message, long ttl) throws IOException {
        HttpPost request = new HttpPost(uri);
        ByteArrayEntity entity = new ByteArrayEntity(message);

        entity.setContentType("application/octet-stream");
        request.setEntity(entity);
        request.setHeader("x-trend-goc-ttl", Long.toString(ttl));

        HttpResponse response = m_http_client.execute(request);
        StatusLine status = response.getStatusLine();
        Header location;

        if(status.getStatusCode() != 201) {
            throw new IOException(status.toString());
        }

        location = response.getFirstHeader("Location");
        if(location == null) {
            throw new IOException("Location not found.");
        }
        else if(location.getValue().length() == 0) {
            throw new IOException("Location is invalid");
        }
        else {
            return location.getValue();
        }
    }

    public void close() {
        m_http_client.getConnectionManager().shutdown();
    }
}
