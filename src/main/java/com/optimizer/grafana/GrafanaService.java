package com.optimizer.grafana;

import com.google.common.collect.Lists;
import com.optimizer.util.OptimizerUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.optimizer.grafana.GrafanaQueryUtils.SERVICE_LIST_PATTERN;
import static com.optimizer.grafana.GrafanaQueryUtils.SERVICE_LIST_QUERY;
import static com.optimizer.util.OptimizerUtils.QUERY;
import static com.optimizer.util.OptimizerUtils.getHttpResponse;

/***
 Created by mudit.g on Feb, 2019
 ***/
public class GrafanaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrafanaService.class.getSimpleName());
    private static final int PARTITION_SIZE = 20;

    private HttpClient client;

    public GrafanaService(HttpClient client) {
        this.client = client;
    }

    public List<HttpResponse> execute(List<String> queries) throws Exception {
        List<HttpResponse> responses = new ArrayList<>();
        for(List<String> queryChunk : Lists.partition(queries, PARTITION_SIZE)) {
            String query = String.join(";", queryChunk);
            query = String.format(QUERY, query);
            HttpResponse response = getHttpResponse(client, query);
            responses.add(response);
            if(response == null) {
                return Collections.emptyList();
            }
        }
        return responses;
    }

    public List<String> getAllServices() {
        try {
            List<String> services = new ArrayList<>();
            String query = String.format(QUERY, SERVICE_LIST_QUERY);

            HttpResponse response = getHttpResponse(client, query);
            if(response == null) {
                return Collections.emptyList();
            }

            String data = EntityUtils.toString(response.getEntity());
            JSONArray serviceJSONArray = OptimizerUtils.getValuesFromMeasurementResponseData(data);
            if(serviceJSONArray == null) {
                LOGGER.error("Error in getting value from data: " + data);
                return Collections.emptyList();
            }
            Pattern pattern = Pattern.compile(SERVICE_LIST_PATTERN);
            for(int i = 0; i < serviceJSONArray.length(); i++) {
                String metrics = ((JSONArray)serviceJSONArray.get(i)).get(0)
                        .toString();
                Matcher matcher = pattern.matcher(metrics);
                if(matcher.find()) {
                    services.add(matcher.group(1));
                } else {
                    LOGGER.error("Match not found for: " + metrics);
                }
            }
            return services;
        } catch (Exception e) {
            LOGGER.error("Error in getting list of services: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
