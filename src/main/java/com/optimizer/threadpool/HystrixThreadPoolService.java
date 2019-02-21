package com.optimizer.threadpool;

import com.collections.CollectionUtils;
import com.google.common.collect.Lists;
import com.optimizer.util.OptimizerUtils;
import lombok.Builder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 Created by mudit.g on Feb, 2019
 ***/
@Builder
public class HystrixThreadPoolService {
    private static final Logger logger = LoggerFactory.getLogger(HystrixThreadPoolService.class.getSimpleName());
    private static final String HYSTRIX_POOL_LIST = "SHOW MEASUREMENTS with measurement = /phonepe.prod.%s.HystrixThreadPool.*.propertyValue_corePoolSize/";
    private static final String HYSTRIX_POOL_NAME_PATTERN = "phonepe.prod.(.*).HystrixThreadPool.(.*).propertyValue_corePoolSize";
    private static final String POOL_CORE_QUERY = "SELECT max(\"value\") FROM \"phonepe.prod.%s.HystrixThreadPool.%s.propertyValue_corePoolSize\" WHERE time > now() - 48h fill(null)";
    private static final String POOL_USAGE_QUERY = "select percentile(\"max\", %s) from (SELECT max(\"value\") FROM \"phonepe.prod.%s.HystrixThreadPool.%s.rollingMaxActiveThreads\" WHERE time > now() - 72h group by time(1m) fill(null))";

    private HttpClient client;
    private Service service;

    public HystrixThreadPoolService(HttpClient client, Service service) {
        this.client = client;
        this.service = service;
    }

    public void handleHystrixPools() {
        List<String> services;
        try {
            services = service.getAllServices();
            if(CollectionUtils.isEmpty(services)) {
                logger.error("Error in getting list of services. Got services = null");
                return;
            }
        } catch (Exception e) {
            logger.error("Error in getting list of services: " + e.getMessage(), e);
            return;
        }
        for(String serviceName : CollectionUtils.nullAndEmptySafeValueList(services)) {
            List<String> hystrixPools;
            try {
                hystrixPools = getHystrixPoolList(serviceName);
                if(hystrixPools == null) {
                    logger.error("Error in getting hystrix pool list for Service: " + serviceName + ". Got hystrixPools = null");
                    continue;
                }
            } catch (Exception e) {
                logger.error("Error in getting hystrix pool list for Service: " + serviceName +
                        " Error Message: " + e.getMessage(), e);
                continue;
            }
            List<Integer> poolsCore = hystrixPoolsCore(serviceName, hystrixPools);
            if(poolsCore == null) {
                logger.error("Error in getting hystrix pools core list for Service: " + serviceName + ". Got poolsCore = null");
                continue;
            }
            List<Integer> poolsUsage = hystrixPoolsUsage(serviceName, hystrixPools);
            if(poolsUsage == null) {
                logger.error("Error in getting hystrix pools usage list for Service: " + serviceName + ". Got poolsUsage = null");
                continue;
            }
            String pool;
            int poolCore;
            int poolUsage;
            int totalPoolCore = 0;
            int reduceBy;
            int canBeFreed = 0;
            for(int poolCount = 0; poolCount < hystrixPools.size(); poolCount++) {
                pool = hystrixPools.get(0);
                poolCore = poolsCore.get(0);
                poolUsage = poolsUsage.get(0);
                if(poolCore <= 0 || poolUsage <= 0) {
                    continue;
                }
                totalPoolCore += poolCore;
                if(poolCore - poolUsage > 0) {
                    reduceBy = poolCore - poolUsage;
                } else {
                    reduceBy = 0;
                }
                canBeFreed += reduceBy;
//                logger.info(String.format("Service: %s Type: HYSTRIX Pool: %s Core: %s Usage: %s Free: %s",
//                        serviceName, pool, poolCore, poolUsage, reduceBy));
            }
            logger.info(String.format("Service: %s Type: HYSTRIX Total: %s Free: %s",
                    serviceName, totalPoolCore, canBeFreed));
        }
    }

    private List<Integer> hystrixPoolsCore(String serviceName, List<String> hystrixPools) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            String poolCoreQuery = String.format(POOL_CORE_QUERY, serviceName, hystrixPool);
            queries.add(poolCoreQuery);
        }
        List<String> grafanaResponse;
        try {
            grafanaResponse = runGrafanaQueries(queries);
            if(grafanaResponse == null) {
                logger.error("Error in getting pool core grafana response. Got grafanaResponse = null");
                return null;
            }
        } catch (Exception e) {
            logger.error("Error in running pool core grafana queries: " + e.getMessage(), e);
            return null;
        }
        List<Integer> responses = new ArrayList<>();
        for(String response : CollectionUtils.nullAndEmptySafeValueList(grafanaResponse)) {
            responses.add(getValueFromGrafanaResponse(response));
        }
        return responses;
    }

    private List<Integer> hystrixPoolsUsage(String serviceName, List<String> hystrixPools) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            //TODO What's this 99. It should be a variable. No one else would ever understand that this is percentile
            String poolUsageQuery = String.format(POOL_USAGE_QUERY, 99, serviceName, hystrixPool);
            queries.add(poolUsageQuery);
        }
        List<String> grafanaResponse;
        try {
            grafanaResponse = runGrafanaQueries(queries);
            if(grafanaResponse == null) {
                logger.error("Error in getting pool core grafana response. Got grafanaResponse = null");
                return null;
            }
        } catch (Exception e) {
            logger.error("Error in running pool core grafana queries: " + e.getMessage(), e);
            return null;
        }
        List<Integer> responses = new ArrayList<>();
        for(String response : CollectionUtils.nullAndEmptySafeValueList(grafanaResponse)) {
            responses.add(getValueFromGrafanaResponse(response));
        }
        return responses;
    }

    private int getValueFromGrafanaResponse(String response) {
        JSONObject jsonObject = new JSONObject(response);
        if(jsonObject.has("series") &&
                ((JSONArray) jsonObject.get("series")).length() > 0 &&
                ((JSONObject) ((JSONArray) jsonObject.get("series")).get(0)).has("values") &&
                ((JSONArray) ((JSONObject) ((JSONArray) jsonObject.get("series")).get(0)).get("values")).length() > 0 &&
                ((JSONArray) ((JSONArray) ((JSONObject) ((JSONArray) jsonObject.get("series")).get(0)).get("values")).get(0)).length() > 1) {
                return (int) ((JSONArray)
                                ((JSONArray)
                                        ((JSONObject)
                                                ((JSONArray) jsonObject.get("series")
                                                ).get(0)
                                        ).get("values")
                                ).get(0)
                        ).get(1);
        }
        return -1;
    }

    //TODO It should be a service and should return the response only. Calling service should parse the response and extract values
    private List<String> runGrafanaQueries(List<String> queries) throws Exception {
        List<String> results = new ArrayList<>();
        for(List<String> queryChunk : Lists.partition(queries, 20)) {
            String query = String.join(";", queryChunk);
            query = String.format("%s;", query);
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
                return Collections.emptyList();
            }
            String data = EntityUtils.toString(response.getEntity());
            JSONObject jsonObject = new JSONObject(data);
            if(jsonObject.has("results")) {
                ((JSONArray) jsonObject.get("results")).forEach(e -> results.add(e.toString()));
            }
        }
        return results;
    }

    private List<String> getHystrixPoolList(String serviceName) throws Exception {
        List<String> poolNames = new ArrayList<>();
        String hystrixPoolList = String.format(HYSTRIX_POOL_LIST, serviceName);
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
        //TODO Pool name might not match with service name. Either we need to introduce nomenclature for pool naming
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
        return poolNames;
    }

}
