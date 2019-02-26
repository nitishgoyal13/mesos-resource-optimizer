package com.optimizer.threadpool;

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

import static com.optimizer.util.OptimizerUtils.QUERY;
import static com.optimizer.util.OptimizerUtils.getHttpResponse;

/***
 Created by mudit.g on Feb, 2019
 ***/
public class Service {
    private static final Logger logger = LoggerFactory.getLogger(Service.class.getSimpleName());
    private static final String SERVICE_LIST_QUERY = "SHOW MEASUREMENTS with measurement = /phonepe.prod.*.jvm.threads.count/";
    private static final String SERVICE_LIST_PATTERN = "phonepe.prod.(.*).jvm.threads.count";

    private HttpClient client;

    public Service(HttpClient client) {
        this.client = client;
    }

    public List<String> getAllServices() throws Exception {
        List<String> services = new ArrayList<>();
        String query = String.format(QUERY, SERVICE_LIST_QUERY);

        HttpResponse response = getHttpResponse(client, query);
        if(response == null) {
            return Collections.emptyList();
        }

        String data = EntityUtils.toString(response.getEntity());
        JSONArray serviceJSONArray = OptimizerUtils.getValuesFromMeasurementResponseData(data);
        if(serviceJSONArray == null) {
            logger.error("Error in getting value from data: " + data);
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
                logger.error("Match not found for: " + metrics);
            }
        }
        return services;
    }
}
