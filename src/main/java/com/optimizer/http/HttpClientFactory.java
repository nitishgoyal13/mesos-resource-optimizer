package com.optimizer.http;

import org.apache.http.Consts;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HTTP;

import java.nio.charset.CodingErrorAction;
import java.util.concurrent.TimeUnit;

/***
 Created by nitish.goyal on 08/03/19
 ***/
public class HttpClientFactory {

    private HttpClientFactory() {
    }

    private static final int MAX_CONNECTION_POOL_SIZE = 1000;
    private static final int MAX_CONNECTION_PER_ROUTE = 1000;
    private static final int IDLE_CONNECTION_TIMEOUT = 5;
    private static final int VALIDATE_AFTER_INACTIVITY_IN_MS = 100;

    public static CloseableHttpClient getHttpClient() {
        // Create socket configuration
        SocketConfig socketConfig = SocketConfig.custom()
                .setTcpNoDelay(true)
                .setSoKeepAlive(true)
                .setSoTimeout(0)
                .build();

        // Create connection configuration
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setMalformedInputAction(CodingErrorAction.IGNORE)
                .setUnmappableInputAction(CodingErrorAction.IGNORE)
                .setCharset(Consts.UTF_8)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(MAX_CONNECTION_PER_ROUTE);
        connectionManager.setMaxTotal(MAX_CONNECTION_POOL_SIZE);
        connectionManager.setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY_IN_MS);
        connectionManager.closeIdleConnections(IDLE_CONNECTION_TIMEOUT, TimeUnit.MINUTES);

        connectionManager.setDefaultSocketConfig(socketConfig);

        connectionManager.setDefaultConnectionConfig(connectionConfig);


        // Create global request configuration
        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectTimeout(Integer.MAX_VALUE)
                .setConnectionRequestTimeout(Integer.MAX_VALUE)
                .setSocketTimeout(0)
                .build();


        final HttpClientBuilder client = HttpClients.custom()
                .addInterceptorFirst((HttpRequestInterceptor)(httpRequest, httpContext) -> httpRequest.removeHeaders(HTTP.CONTENT_LEN))
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(defaultRequestConfig);

        return client.build();
    }
}
