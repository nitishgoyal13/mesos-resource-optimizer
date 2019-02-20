package com.optimizer.util;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/***
 Created by nitish.goyal on 18/02/19
 ***/
public class OptimizerUtils {
    private static final String URL_TEMPLATE = "http://prd-grafana001.phonepe.nm1/api/datasources/proxy/1/query?db=riemann_metrics&q=%s";
    private static final String ENCODING = "UTF-8";
    private static Map<String, String> GRAFANA_HEADERS = new HashMap<String, String>() {{
        put("Referer", "http://prd-grafana001.phonepe.nm1/dashboard/db/api-hystrix");
        put("Cookie", "grafana_user=admin; grafana_sess=cd9ad618a07791d8; grafana_remember=97431358d8af8b6e873e9337dd3fc797f0feb0aac63aebf9");
    }};

    public static HttpResponse executeGetRequest(HttpClient client, String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, ENCODING);
        String url = String.format(URL_TEMPLATE, encodedQuery);
        HttpGet request = new HttpGet(url);
        GRAFANA_HEADERS.forEach(request::addHeader);
        return client.execute(request);
    }
}
