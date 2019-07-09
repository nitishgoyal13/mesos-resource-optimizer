package com.optimizer.grafana;

import static com.optimizer.grafana.GrafanaQueryUtils.APP_LIST_PATTERN;
import static com.optimizer.grafana.GrafanaQueryUtils.APP_LIST_QUERY;
import static com.optimizer.grafana.GrafanaQueryUtils.POOL_LIST_PATTERN;
import static com.optimizer.grafana.GrafanaQueryUtils.POOL_LIST_QUERY;
import static com.optimizer.util.OptimizerUtils.ExtractionStrategy;
import static com.optimizer.util.OptimizerUtils.INDEX_ONE;
import static com.optimizer.util.OptimizerUtils.INDEX_ZERO;
import static com.optimizer.util.OptimizerUtils.QUERY;
import static com.optimizer.util.OptimizerUtils.RESULTS;
import static com.optimizer.util.OptimizerUtils.SERIES;
import static com.optimizer.util.OptimizerUtils.STATUS_OK_RANGE_END;
import static com.optimizer.util.OptimizerUtils.STATUS_OK_RANGE_START;
import static com.optimizer.util.OptimizerUtils.VALUES;
import static com.optimizer.util.OptimizerUtils.getArrayFromJSONObject;
import static com.optimizer.util.OptimizerUtils.getAvgValueFromJsonArray;
import static com.optimizer.util.OptimizerUtils.getHttpResponse;
import static com.optimizer.util.OptimizerUtils.getMaxValueFromJsonArray;
import static com.optimizer.util.OptimizerUtils.getObjectFromJSONArray;

import com.collections.CollectionUtils;
import com.google.common.collect.Lists;
import com.optimizer.config.GrafanaConfig;
import com.optimizer.util.OptimizerUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 Created by mudit.g on Feb, 2019
 ***/
@Builder
@AllArgsConstructor
@Data
public class GrafanaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrafanaService.class.getSimpleName());
    private static final int PARTITION_SIZE = 10;

    private HttpClient client;
    private GrafanaConfig grafanaConfig;

    private static long getValueFromGrafanaResponse(String response, ExtractionStrategy extractionStrategy) {
        JSONObject jsonObject = new JSONObject(response);
        JSONArray seriesJSONArray = getArrayFromJSONObject(jsonObject, SERIES);
        JSONObject seriesJSONObject = getObjectFromJSONArray(seriesJSONArray, INDEX_ZERO);
        JSONArray valuesJSONArray = getArrayFromJSONObject(seriesJSONObject, VALUES);
        switch (extractionStrategy) {
            case MAX:
                return getMaxValueFromJsonArray(valuesJSONArray);
            case AVERAGE:
                return getAvgValueFromJsonArray(valuesJSONArray);
            default:
                return getMaxValueFromJsonArray(valuesJSONArray);
        }
    }

    public List<HttpResponse> execute(List<String> queries) {
        List<HttpResponse> responses = new ArrayList<>();
        for (List<String> queryChunk : Lists.partition(queries, 1)) {
            String query = String.join(";", queryChunk);
            query = String.format(QUERY, query);
            HttpResponse response = getHttpResponse(client, query, grafanaConfig);
            if (response != null) {
                responses.add(response);
            }
        }
        return responses;
    }

    public HttpResponse execute(String query) {
        query = String.format(QUERY, query);
        return getHttpResponse(client, query, grafanaConfig);
    }

    public Map<String, List<String>> getServiceVsPoolList(String prefix, String clusterName) {
        Map<String, List<String>> serviceVsPoolList = new HashMap<>();
        try {
            String poolListQuery = String.format(POOL_LIST_QUERY, prefix, clusterName);
            HttpResponse response = getHttpResponse(client, poolListQuery, grafanaConfig);
            if (response == null) {
                return Collections.emptyMap();
            }
            String data = EntityUtils.toString(response.getEntity());
            JSONArray serviceJsonArray = OptimizerUtils.getValuesFromMeasurementResponseData(data);
            if (serviceJsonArray == null) {
                LOGGER.error("Error in getting value from data: {} ", data);
                return Collections.emptyMap();
            }
            String poolListPattern = String.format(POOL_LIST_PATTERN, prefix, clusterName);
            Pattern pattern = Pattern.compile(poolListPattern);
            for (int i = 0; i < serviceJsonArray.length(); i++) {
                String metrics = ((JSONArray) serviceJsonArray.get(i)).get(0)
                        .toString();
                Matcher matcher = pattern.matcher(metrics);
                if (matcher.find()) {
                    String pool = matcher.group(INDEX_ONE);
                    String service = pool.split("\\.")[0];
                    if (serviceVsPoolList.containsKey(service)) {
                        serviceVsPoolList.get(service)
                                .add(pool);
                    } else {
                        serviceVsPoolList.put(service, Lists.newArrayList(pool));
                    }
                } else {
                    LOGGER.error("Match not found for: {}", metrics);
                }
            }
            return serviceVsPoolList;
        } catch (Exception e) {
            LOGGER.error("Error in getting list of services: " + e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    public List<String> getAppList(String prefix) {
        List<String> appList = new ArrayList<>();
        try {
            String appListQuery = String.format(APP_LIST_QUERY, prefix);
            String query = String.format(QUERY, appListQuery);

            HttpResponse response = getHttpResponse(client, query, grafanaConfig);
            if (response == null) {
                return Collections.emptyList();
            }

            String data = EntityUtils.toString(response.getEntity());
            JSONArray serviceJsonArray = OptimizerUtils.getValuesFromMeasurementResponseData(data);
            if (serviceJsonArray == null) {
                LOGGER.error("Error in getting value from data: {} ", data);
                return Collections.emptyList();
            }
            String appListPattern = String.format(APP_LIST_PATTERN, prefix);
            Pattern pattern = Pattern.compile(appListPattern);
            for (int i = 0; i < serviceJsonArray.length(); i++) {
                String metrics = ((JSONArray) serviceJsonArray.get(i)).get(0)
                        .toString();
                Matcher matcher = pattern.matcher(metrics);
                if (matcher.find()) {
                    String appId = matcher.group(INDEX_ONE);
                    if (!appList.contains(appId)) {
                        appList.add(appId);
                    }
                } else {
                    LOGGER.error("Match not found for: {}", metrics);
                }
            }
            return appList;
        } catch (Exception e) {
            LOGGER.error("Error in getting list of apps: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public Map<String, Long> executeQueriesAndGetMapWithEntity(List<String> queries, List<String> entities,
            ExtractionStrategy extractionStrategy) throws Exception {
        Map<String, Long> entityVsResult = new HashMap<>();
        int index = 0;
        for (List<String> queryChunk : Lists.partition(queries, PARTITION_SIZE)) {
            List<HttpResponse> httpResponses = execute(queryChunk);
            if (CollectionUtils.isEmpty(httpResponses)) {
                return Collections.emptyMap();
            }
            for (HttpResponse httpResponse : httpResponses) {
                try {
                    int status = httpResponse.getStatusLine()
                            .getStatusCode();
                    if (status < STATUS_OK_RANGE_START || status >= STATUS_OK_RANGE_END) {
                        LOGGER.error("Error in Http get, Status Code: {} with received Response: {}",
                                httpResponse.getStatusLine()
                                        .getStatusCode(), httpResponse);
                        return Collections.emptyMap();
                    }
                    String data = EntityUtils.toString(httpResponse.getEntity());
                    JSONObject jsonObject = new JSONObject(data);
                    if (jsonObject.has(RESULTS)) {
                        JSONArray resultArray = (JSONArray) jsonObject.get(RESULTS);
                        for (int resultIndex = 0; resultIndex < resultArray.length(); resultIndex++) {
                            long result = getValueFromGrafanaResponse(resultArray.get(resultIndex)
                                    .toString(), extractionStrategy);
                            entityVsResult.put(entities.get(index), result);
                            index++;
                        }
                    }
                } finally {
                    if (httpResponse.getEntity() != null) {
                        EntityUtils.consume(httpResponse.getEntity());
                    }
                }
            }
        }
        return entityVsResult;
    }
}
