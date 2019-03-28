package com.optimizer.util;

import com.optimizer.grafana.config.GrafanaConfig;
import com.optimizer.threadpool.HystrixThreadPoolService;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;

/***
 Created by nitish.goyal on 18/02/19
 ***/
public class OptimizerUtils {

    public static final String QUERY = "%s;";
    private static final Logger LOGGER = LoggerFactory.getLogger(OptimizerUtils.class);

    private static final String ENCODING = "UTF-8";
    public static final String RESULTS = "results";
    public static final String SERIES = "series";
    public static final String VALUES = "values";
    public static final int INDEX_ZERO = 0;
    public static final int INDEX_ONE = 1;
    public static final int NULL_VALUE = -1;
    public static final int STATUS_OK_RANGE_START = 200;
    public static final int STATUS_OK_RANGE_END = 300;
    public static final String MAIL_SUBJECT = "Thread Pool Optimization";

    public enum ExtractionStrategy {
        AVERAGE,
        MAX
    }

    private static HttpResponse executeGetRequest(HttpClient client, String query, GrafanaConfig grafanaConfig) throws Exception {
        String encodedQuery = URLEncoder.encode(query, ENCODING);
        String url = String.format(grafanaConfig.getUrl(), encodedQuery);
        HttpGet request = new HttpGet(url);
        grafanaConfig.getHeaders()
                .forEach(header -> request.addHeader(header.getName(), header.getValue()));
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
            return (JSONArray)jsonObject.get(key);
        }
        return null;
    }

    public static JSONObject getObjectFromJSONArray(JSONArray jsonArray, int index) {
        if(jsonArray != null && jsonArray.length() > index) {
            return (JSONObject)jsonArray.get(index);
        }
        return null;
    }

    public static JSONObject getObjectFromJSONObject(JSONObject jsonObject, String key) {
        if(jsonObject != null && jsonObject.has(key)) {
            return (JSONObject)jsonObject.get(key);
        }
        return null;
    }

    public static String getStringFromJSONObject(JSONObject jsonObject, String key) {
        if(jsonObject != null && jsonObject.has(key)) {
            return (String)jsonObject.get(key);
        }
        return null;
    }

    public static long getMaxValueFromJsonArray(JSONArray jsonArray) {
        if(jsonArray == null) {
            return NULL_VALUE;
        }
        long maxValue = NULL_VALUE;
        for(int index = 0; index < jsonArray.length(); index++) {
            JSONArray array = (JSONArray)jsonArray.get(index);
            if(array != null && array.length() > 1) {
                if(array.get(INDEX_ONE) instanceof Integer && (int)array.get(INDEX_ONE) > maxValue) {
                    maxValue = (int)array.get(INDEX_ONE);
                } else if(array.get(INDEX_ONE) instanceof Double && ((Double)array.get(INDEX_ONE)).intValue() > maxValue) {
                    maxValue = ((Double)array.get(INDEX_ONE)).longValue();
                } else if(array.get(INDEX_ONE) instanceof Long && ((Long)array.get(INDEX_ONE)) > maxValue){
                    maxValue = (Long)array.get(INDEX_ONE);
                }
            }
        }
        return maxValue;
    }

    public static long getAvgValueFromJsonArray(JSONArray jsonArray) {
        if(jsonArray == null) {
            return NULL_VALUE;
        }
        long sumOfValues = 0;
        int values = 0;
        for(int index = 0; index < jsonArray.length(); index++) {
            JSONArray array = (JSONArray)jsonArray.get(index);
            if(array != null && array.length() > 1) {
                if(array.get(INDEX_ONE) instanceof Integer) {
                    sumOfValues += (int)array.get(INDEX_ONE);
                    values++;
                } else if(array.get(INDEX_ONE) instanceof Double) {
                    sumOfValues += ((Double)array.get(INDEX_ONE)).longValue();
                    values++;
                } else if(array.get(INDEX_ONE) instanceof Long) {
                    sumOfValues += (Long)array.get(INDEX_ONE);
                    values++;
                }
            }
        }
        if(values > 0) {
            return sumOfValues / values;
        }
        return NULL_VALUE;
    }

    public static HttpResponse getHttpResponse(HttpClient client, String query, GrafanaConfig grafanaConfig) {
        try {
            HttpResponse response = executeGetRequest(client, query, grafanaConfig);
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
