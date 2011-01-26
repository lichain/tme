package com.trendmicro.mist.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import com.trendmicro.codi.ZNode;
import com.trendmicro.spn.common.util.Utils;
import com.trendmicro.spn.proto.SpnMessage.Container;
import com.trendmicro.spn.proto.SpnMessage.ContainerBase;
import com.trendmicro.spn.proto.SpnMessage.MessageList;
import com.trendmicro.spn.proto.SpnMessage.OutOfBandObject;
import com.trendmicro.spn.proto.SpnMessage.Timestamp;

public final class GOCUtils {
    private static Log logger = LogFactory.getLog(GOCUtils.class);
    private HttpClient m_http_client;
    private int m_goc_idx = 0;
    private long m_last_update = 0;
    private List<URI> m_uri_list = new java.util.ArrayList<URI>();

    private static ResponseHandler<byte[]> getRespHandler = new ResponseHandler<byte[]>() {
        public byte[] handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
            HttpEntity entity = response.getEntity();
            // If the response does not enclose an entity, there is no need
            // to worry about connection release
            if (entity != null && entity.getContentLength() != -1) {
            	if (response.getStatusLine().getStatusCode() != 200 ) {
            		entity.consumeContent();
            		throw new IOException(response.getStatusLine().toString());
            	}
                return EntityUtils.toByteArray(entity);
            } else {
                return null;
            }
        }
    };

    private static ResponseHandler<Header> postRespHandler = new ResponseHandler<Header>() {
        public Header handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
            	if (response.getStatusLine().getStatusCode() != 201 ) {
            		entity.consumeContent();
            		throw new IOException(response.getStatusLine().toString());
            	}
            	Header location = response.getFirstHeader("Location");
            	// consume remaining response content to ensure the connection gets released to the manager
            	entity.consumeContent();
                return location;
            } else {
                return null;
            }
        }
    };


    public GOCUtils() {
        initThreadSafeMgrBHttpClient();
    }

    private void initThreadSafeMgrBHttpClient() {
		HttpParams params = new BasicHttpParams();

		ConnManagerParams.setMaxTotalConnections(params, 100);
		// Increase default max connection per route to 20
		ConnPerRouteBean connPerRoute = new ConnPerRouteBean(20);
		ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);

		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

		ThreadSafeClientConnManager mgr = new ThreadSafeClientConnManager(params, schemeRegistry);

		m_http_client = new DefaultHttpClient(mgr, params);
	}

    public byte[] get(URI uri) throws IOException {
    	HttpGet httpget = new HttpGet(uri);
        return m_http_client.execute(httpget, getRespHandler);
    }

    public String post(URI uri, byte[] message, long expire) throws IOException {
        HttpPost request = new HttpPost(uri);
        ByteArrayEntity entity = new ByteArrayEntity(message);

        entity.setContentType("application/octet-stream");
        request.setEntity(entity);
        request.setHeader("x-trend-goc-expire", Long.toString(expire));

        Header location = m_http_client.execute(request, postRespHandler);

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

    public byte [] GOCPack(byte [] message, long expire) {
        byte[] message_ref = null;
        URI goc_server = null;

        ContainerBase containerBase = null;
        Container container = null;
        Container.Builder cont_builder = null;
        try {
            cont_builder = Container.newBuilder();
            cont_builder.mergeFrom(message);
            container = cont_builder.build();
            containerBase = container.getContainerBase();
        }
        catch(Exception e) {
            return null;
        }

        while((goc_server = getGOCUri()) != null) {
            try{
                if(containerBase.hasMessageList()) {
                    String location = post(goc_server, containerBase.getMessageList().toByteArray(), expire);

                    OutOfBandObject.Builder oob_builder = OutOfBandObject.newBuilder();
                    oob_builder.setUrl(location);
                    oob_builder.setExpire(Timestamp.newBuilder().setTime(expire).build());

                    ContainerBase.Builder cb_builder = ContainerBase.newBuilder();
                    cb_builder.mergeFrom(container.getContainerBase().toByteArray());
                    cb_builder.clearMessageList();
                    cb_builder.setMessageListRef(oob_builder.build());

                    cont_builder = Container.newBuilder();
                    cont_builder.mergeFrom(container.toByteArray());
                    cont_builder.setContainerBase(cb_builder.build());
                    container = cont_builder.build();
                    message_ref = container.toByteArray();
                }
                else
                    message_ref = message;

                break;
            }
            catch (IOException ioe) {
            	logger.error(ioe.getMessage());
            	continue;
            }
            catch(Exception e) {
                logger.error(e.getMessage());
                break;
            }
        }

        return message_ref;
    }

    public byte [] GOCUnPack(byte [] message_ref) {
        byte [] message = null;
        try {
            Container.Builder cont_builder = Container.newBuilder();
            cont_builder.mergeFrom(message_ref);
            Container container = cont_builder.build();
            ContainerBase containerBase = container.getContainerBase();
            if(containerBase.hasMessageListRef()) {
                byte [] object = get(new URI(containerBase.getMessageListRef().getUrl()));

                MessageList.Builder msg_list_builder = MessageList.newBuilder();
                msg_list_builder.mergeFrom(object);

                ContainerBase.Builder cb_builder = ContainerBase.newBuilder();
                cb_builder.mergeFrom(containerBase);
                cb_builder.clearMessageListRef();
                cb_builder.setMessageList(msg_list_builder.build());

                cont_builder = Container.newBuilder();
                cont_builder.mergeFrom(container.toByteArray());
                cont_builder.setContainerBase(cb_builder.build());
                container = cont_builder.build();
                message = container.toByteArray();
            }
            else
                message = message_ref;
        }
        catch(Exception e) {
            logger.error(e.getMessage());
        }
        return message;
    }

    private URI getGOCUri() {
    	final int INTERVAL = 300 * 1000; // 5 mins
    	long now = new Date().getTime();

    	while (now - m_last_update > INTERVAL) {
			m_uri_list.clear();
			m_last_update = now;

			try {
				String goc_node = new String(new ZNode("/tme2/global/goc_server").getContent());

				try {
					URI uri = new URI(goc_node);
					URL server_url = uri.toURL();
					String host = server_url.getHost() + ":" + server_url.getPort();
					if (Utils.checkSocketConnectable(host)) {
						m_uri_list.add(uri);
						break;
					}
				} catch (Exception e) {
				}

				List<InetAddress> servers = Arrays.asList(InetAddress.getAllByName(goc_node));
				Collections.shuffle(servers);

				Iterator<InetAddress> iter = servers.iterator();
				while (iter.hasNext()) {
					InetAddress address = iter.next();
					if (Utils.checkSocketConnectable(address.getHostAddress(), 80))
						m_uri_list.add(new URI("http", null, address.getHostAddress(), 80, "/depot/*", null, null));
				}
			} catch (Exception e) {
				logger.error(e.getMessage());
			}
    	}
		if (m_uri_list.size() == 0) {
			return null;
		} else {
			m_goc_idx %= m_uri_list.size();
			return m_uri_list.toArray(new URI[0])[m_goc_idx++];
		}
    }

    public void close() {
        m_http_client.getConnectionManager().shutdown();
    }

    protected void finalize() throws Throwable
    {
    	close();
    }
}
