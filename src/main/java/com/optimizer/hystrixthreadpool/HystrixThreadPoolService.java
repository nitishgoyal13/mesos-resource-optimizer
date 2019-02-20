package com.optimizer.hystrixthreadpool;

import com.collections.CollectionUtils;
import com.optimizer.util.OptimizerUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 Created by mudit.g on Feb, 2019
 ***/
public class HystrixThreadPoolService {
    private static final Logger logger = LoggerFactory.getLogger(HystrixThreadPoolService.class.getSimpleName());
    private static final String HYSTRIX_POOL_LIST = "SHOW MEASUREMENTS with measurement = /phonepe.prod.%s.HystrixThreadPool.*.propertyValue_corePoolSize/";
    private static final String HYSTRIX_POOL_NAME_PATTERN = "phonepe.prod.(.*).HystrixThreadPool.(.*).propertyValue_corePoolSize";

    private HttpClient client;
    private Service service;

    public HystrixThreadPoolService(HttpClient client, Service service) {
        this.client = client;
        this.service = service;
    }

    private List<String> getHystrixPoolList() throws Exception {
        List<String> poolNames = new ArrayList<>();
        List<String> services;
        try {
            services = service.getAllServices();
            if(services == null) {
                logger.error("Error in getting list of services. Got services = null");
                return null;
            }
        } catch (Exception e) {
            logger.error("Error in getting list of services: " + e.getMessage(), e);
            return null;
        }
        for(String service : CollectionUtils.nullAndEmptySafeValueList(services)) {
            String hystrixPoolList = String.format(HYSTRIX_POOL_LIST, service);
            String query = String.format("%s;", hystrixPoolList);
            HttpResponse response;
            try {
                response = OptimizerUtils.executeGetRequest(client, query);
                int status = response.getStatusLine().getStatusCode();
                if (status < 200 || status >= 300) {
                    logger.error("Error in Http get, Status Code: " + response.getStatusLine().getStatusCode() + " received Response: " + response);
                    return null;
                }
            } catch (Exception e) {
                logger.error("Error in Http get: " + e.getMessage(), e);
                return null;
            }
            String data = EntityUtils.toString(response.getEntity());
            JSONArray poolJSONArray = OptimizerUtils.getValuesFromMeasurementData(data);
            if(poolJSONArray == null) {
                logger.error("Error in getting value from data: " + data);
                return null;
            }
            Pattern pattern = Pattern.compile(HYSTRIX_POOL_NAME_PATTERN);
            for(int i = 0; i < poolJSONArray.length(); i++) {
                String metrics = ((JSONArray) poolJSONArray.get(i)).get(0).toString();
                Matcher matcher = pattern.matcher(metrics);
                if(matcher.find()) {
                    poolNames.add(matcher.group(2));
                } else {
                    logger.error("Match not found for: " + metrics);
                }
            }
        }
        return poolNames;
    }

}
