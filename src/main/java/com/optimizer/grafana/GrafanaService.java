package com.optimizer.grafana;

import com.google.common.collect.Lists;
import com.optimizer.grafana.config.GrafannaConfig;
import com.optimizer.util.OptimizerUtils;
import lombok.Builder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.optimizer.grafana.GrafanaQueryUtils.POOL_LIST_PATTERN;
import static com.optimizer.grafana.GrafanaQueryUtils.POOL_LIST_QUERY;
import static com.optimizer.util.OptimizerUtils.*;

/***
 Created by mudit.g on Feb, 2019
 ***/
@Builder
public class GrafanaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrafanaService.class.getSimpleName());
    private static final int PARTITION_SIZE = 20;

    private HttpClient client;
    private GrafannaConfig grafannaConfig;

    public GrafanaService(HttpClient client, GrafannaConfig grafannaConfig) {
        this.client = client;
        this.grafannaConfig = grafannaConfig;
    }

    public List<HttpResponse> execute(List<String> queries) {
        List<HttpResponse> responses = new ArrayList<>();
        for(List<String> queryChunk : Lists.partition(queries, PARTITION_SIZE)) {
            String query = String.join(";", queryChunk);
            query = String.format(QUERY, query);
            HttpResponse response = getHttpResponse(client, query, grafannaConfig);
            responses.add(response);
            if(response == null) {
                return Collections.emptyList();
            }
        }
        return responses;
    }

    public Map<String, List<String>> getServiceVsPoolList(String clusterName) {
        Map<String, List<String>> serviceVsPoolList = new HashMap<>();
        try {
            String poolListQuery = String.format(POOL_LIST_QUERY, clusterName);
            String query = String.format(QUERY, poolListQuery);

            HttpResponse response = getHttpResponse(client, query, grafannaConfig);
            if(response == null) {
                return Collections.emptyMap();
            }

            String data = EntityUtils.toString(response.getEntity());
            JSONArray serviceJsonArray = OptimizerUtils.getValuesFromMeasurementResponseData(data);
            if(serviceJsonArray == null) {
                LOGGER.error("Error in getting value from data: " + data);
                return Collections.emptyMap();
            }
            String poolListPattern = String.format(POOL_LIST_PATTERN, clusterName);
            Pattern pattern = Pattern.compile(poolListPattern);
            for(int i = 0; i < serviceJsonArray.length(); i++) {
                String metrics = ((JSONArray)serviceJsonArray.get(i)).get(0)
                        .toString();
                Matcher matcher = pattern.matcher(metrics);
                if(matcher.find()) {
                    String pool = matcher.group(INDEX_ONE);
                    String service = pool.split("\\.")[0];
                    if(serviceVsPoolList.containsKey(service)) {
                        serviceVsPoolList.get(service)
                                .add(pool);
                    } else {
                        serviceVsPoolList.put(service, Lists.newArrayList(pool));
                    }
                } else {
                    LOGGER.error("Match not found for: " + metrics);
                }
            }
            return serviceVsPoolList;
        } catch (Exception e) {
            LOGGER.error("Error in getting list of services: " + e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
}
