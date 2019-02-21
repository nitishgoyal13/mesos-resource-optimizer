package com.optimizer.threadpool;

import com.google.common.collect.Lists;
import com.optimizer.util.OptimizerUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.optimizer.util.OptimizerUtils.QUERY;

/***
 Created by mudit.g on Feb, 2019
 ***/
public class GrafanaService {
    private static final Logger logger = LoggerFactory.getLogger(GrafanaService.class.getSimpleName());
    private static final int PARTITION_SIZE = 20;

    private HttpClient client;

    public GrafanaService(HttpClient client) {
        this.client = client;
    }

    public List<HttpResponse> runGrafanaQueries(List<String> queries) throws Exception {
        List<HttpResponse> responses = new ArrayList<>();
        for(List<String> queryChunk : Lists.partition(queries, PARTITION_SIZE)) {
            String query = String.join(";", queryChunk);
            query = String.format(QUERY, query);
            HttpResponse response;
            try {
                response = OptimizerUtils.executeGetRequest(client, query);
                responses.add(response);
            } catch (Exception e) {
                logger.error("Error in Http get: " + e.getMessage(), e);
                return Collections.emptyList();
            }
        }
        return responses;
    }
}
