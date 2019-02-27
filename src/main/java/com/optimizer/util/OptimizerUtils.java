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

    public static final String QUERY = "%s;";
    private static final Logger LOGGER = LoggerFactory.getLogger(OptimizerUtils.class);

    private static final String URL_TEMPLATE = "http://prd-grafana001.phonepe.nm1/api/datasources/proxy/1/query?db=riemann_metrics&q=%s";
    private static final String ENCODING = "UTF-8";
    public static final String RESULTS = "results";
    public static final String SERIES = "series";
    public static final String VALUES = "values";
    public static final int INDEX_ZERO = 0;
    public static final int INDEX_ONE = 1;
    public static final int NULL_VALUE = -1;
    public static final int PERCENTILE = 99;
    public static final int STATUS_OK_RANGE_START = 200;
    public static final int STATUS_OK_RANGE_END = 300;

    //TODO Need to change to api-group thread pools. You will extract the pools and service name from there
    private static Map<String, String> GRAFANA_HEADERS = new HashMap<String, String>() {{
        put("Referer", "http://prd-grafana001.phonepe.nm1/dashboard/db/api-hystrix");
        put("Cookie",
            "grafana_user=admin; grafana_sess=ff16a0e4a23fc218; grafana_remember=d3f77f92a8b5a5b653c1a9358ca58d39d44a076d5485d2de"
           );
    }};

    private static HttpResponse executeGetRequest(HttpClient client, String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, ENCODING);
        String url = String.format(URL_TEMPLATE, encodedQuery);
        HttpGet request = new HttpGet(url);
        //TODO Get these headers from grafana config
        GRAFANA_HEADERS.forEach(request::addHeader);
        return client.execute(request);
    }

    public static JSONArray getValuesFromMeasurementResponseData(String data) {
        JSONObject jsonObject = new JSONObject(data);
        JSONArray resultsJSONArray = getArrayFromJSONObject(jsonObject, RESULTS);
        JSONObject resultsJSONObjects = getObjectFromJSONArray(resultsJSONArray, INDEX_ZERO);
        JSONArray seriesJSONArray = getArrayFromJSONObject(resultsJSONObjects, SERIES);
        JSONObject seriesJSONObject = getObjectFromJSONArray(seriesJSONArray, INDEX_ZERO);
        return getArrayFromJSONObject(seriesJSONObject, VALUES);
    }

    public static JSONArray getArrayFromJSONObject(JSONObject jsonObject, String key) {
        if(jsonObject != null && jsonObject.has(key)) {
            return (JSONArray) jsonObject.get(key);
        }
        return null;
    }

    public static JSONObject getObjectFromJSONArray(JSONArray jsonArray, int index) {
        if(jsonArray != null && jsonArray.length() > index) {
            return (JSONObject) jsonArray.get(index);
        }
        return null;
    }

    public static JSONArray getArrayFromJSONArray(JSONArray jsonArray, int index) {
        if(jsonArray != null && jsonArray.length() > index) {
            return (JSONArray) jsonArray.get(index);
        }
        return null;
    }

    public static HttpResponse getHttpResponse(HttpClient client, String query) {
        try {
            HttpResponse response = executeGetRequest(client, query);
            int status = response.getStatusLine()
                    .getStatusCode();
            if(status < STATUS_OK_RANGE_START || status >= STATUS_OK_RANGE_END) {
                LOGGER.error("Error in Http get, Status Code: " + response.getStatusLine()
                        .getStatusCode() + " received Response: " + response);

                return null;
            }
            return response;
        } catch (Exception e) {
            LOGGER.error("Error in Http get: " + e.getMessage(), e);
            return null;
        }
    }
}
