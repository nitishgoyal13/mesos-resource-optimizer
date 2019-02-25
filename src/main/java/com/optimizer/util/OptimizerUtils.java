package com.optimizer.util;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/***
 Created by nitish.goyal on 18/02/19
 ***/
public class OptimizerUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(OptimizerUtils.class);

    private static final String URL_TEMPLATE = "http://prd-grafana001.phonepe.nm1/api/datasources/proxy/1/query?db=riemann_metrics&q=%s";
    private static final String ENCODING = "UTF-8";
    //TODO Need to change to api-group thread pools. You will extract the pools and service name from there
    private static Map<String, String> GRAFANA_HEADERS = new HashMap<String, String>() {{
        put("Referer", "http://prd-grafana001.phonepe.nm1/dashboard/db/api-hystrix");
        put("Cookie",
            "grafana_user=admin; grafana_sess=09d7b009ca2a6827; grafana_remember=d6b0b5261ede8c68caeba4ef8a18ec5de0c5f23b57c79290"
           );
    }};

    public static HttpResponse executeGetRequest(HttpClient client, String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, ENCODING);
        String url = String.format(URL_TEMPLATE, encodedQuery);
        HttpGet request = new HttpGet(url);
        //TODO Get these headers from grafana config
        GRAFANA_HEADERS.forEach(request::addHeader);
        return client.execute(request);
    }

    public static HttpResponse getHttpResponse(HttpClient client, String query) {
        HttpResponse response;
        try {
            response = executeGetRequest(client, query);
            int status = response.getStatusLine()
                    .getStatusCode();
            if(status < 200 || status >= 300) {
                LOGGER.error("Error in Http get, Status Code: " + response.getStatusLine()
                        .getStatusCode() + " received Response: " + response);

                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Error in Http get: " + e.getMessage(), e);
            return null;
        }
        return response;
    }

    public static JSONArray getValuesFromMeasurementData(String data) {
        JSONObject jsonObject = new JSONObject(data);
        //TODO Get the results and store in variable
        //It would increase the readability. This below code becomes difficult to debug if any error comes
        if(jsonObject.has("results") && ((JSONArray)jsonObject.get("results")).length() > 0 && ((JSONObject)((JSONArray)jsonObject.get(
                "results")).get(0)).has("series") && ((JSONArray)((JSONObject)((JSONArray)jsonObject.get("results")).get(0)).get(
                "series")).length() > 0 && ((JSONObject)((JSONArray)((JSONObject)((JSONArray)jsonObject.get("results")).get(0)).get(
                "series")).get(0)).has("values")) {
            return ((JSONArray)((JSONObject)((JSONArray)((JSONObject)((JSONArray)jsonObject.get("results")).get(0)).get("series")).get(
                    0)).get("values"));
        }
        return null;
    }
}
