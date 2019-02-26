package com.optimizer.threadpool;

import com.collections.CollectionUtils;
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

import static com.optimizer.util.OptimizerUtils.*;

/***
 Created by mudit.g on Feb, 2019
 ***/
@Builder
public class HystrixThreadPoolService {
    private static final Logger logger = LoggerFactory.getLogger(HystrixThreadPoolService.class.getSimpleName());

    //TODO Move all queries to ThreadPoolQueryUtils. Makes the class clean
    private static final String HYSTRIX_POOL_LIST_QUERY =
            "SHOW MEASUREMENTS with measurement = /phonepe.prod.%s.HystrixThreadPool.*.propertyValue_corePoolSize/";
    private static final String HYSTRIX_POOL_NAME_PATTERN = "phonepe.prod.(.*).HystrixThreadPool.(.*).propertyValue_corePoolSize";
    //TODO Core pool query is getting values from 48 hours
    private static final String CORE_POOL_QUERY =
            "SELECT max(\"value\") FROM \"phonepe.prod.%s.HystrixThreadPool.%s.propertyValue_corePoolSize\" WHERE time > now() - 48h fill" +
            "(null)";
    //TODO Core pool query is getting values from 72 hours
    //TODO This 72h should also come from the config
    //Also this group by should be 30s instead of 1m
    private static final String POOL_USAGE_QUERY =
            "select percentile(\"max\", %s) from (SELECT max(\"value\") FROM \"phonepe.prod.%s.HystrixThreadPool.%s" +
            ".rollingMaxActiveThreads\" WHERE time > now() - 72h group by time(1m) fill(null))";

    private HttpClient client;
    private Service service;
    private GrafanaService grafanaService;

    public HystrixThreadPoolService(HttpClient client, Service service, GrafanaService grafanaService) {
        this.client = client;
        this.service = service;
        this.grafanaService = grafanaService;
    }

    public void handleHystrixPools() {
        List<String> services;
        try {
            services = service.getAllServices();
            if(CollectionUtils.isEmpty(services)) {
                logger.error("Error in getting list of services. Got services = []");
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
                if(CollectionUtils.isEmpty(hystrixPools)) {
                    logger.error("Error in getting hystrix pool list for Service: " + serviceName + ". Got hystrixPools = []");
                    continue;
                }
            } catch (Exception e) {
                logger.error("Error in getting hystrix pool list for Service: " + serviceName + " Error Message: " + e.getMessage(), e);
                continue;
            }
            //TODO See if you can combine hystrixPoolsCore and hystrixPoolsUsage into one method. It should be generic method inside
            // grafana service. Pass the query from outside and grafana service should return the response
            //TODO It should return map of hystrix pool vs corePoolSize
            List<Integer> poolsCore = hystrixPoolsCore(serviceName, hystrixPools);
            if(CollectionUtils.isEmpty(poolsCore)) {
                logger.error("Error in getting hystrix pools core list for Service: " + serviceName + ". Got poolsCore = []");
                continue;
            }
            //TODO It should return map of hystrix pool vs poolUsage
            List<Integer> poolsUsage = hystrixPoolsUsage(serviceName, hystrixPools);
            if(CollectionUtils.isEmpty(poolsUsage)) {
                logger.error("Error in getting hystrix pools usage list for Service: " + serviceName + ". Got poolsUsage = []");
                continue;
            }
            String pool;
            int corePool;
            int poolUsage;
            int totalCorePool = 0;
            int reduceBy;
            int canBeFreed = 0;
            for(int poolCount = 0; poolCount < hystrixPools.size(); poolCount++) {
                //TODO Get these corePool and poolUsage from the Map.
                pool = hystrixPools.get(0);
                corePool = poolsCore.get(poolCount);
                poolUsage = poolsUsage.get(poolCount);
                if(corePool <= 0 || poolUsage <= 0) {
                    continue;
                }
                totalCorePool += corePool;
                //TODO There should be some percentage difference which should come from config. Only is poolUsage is less than 50% of
                // corePool, then you should calculate reduce by
                if(corePool - poolUsage > 0) {
                    reduceBy = corePool - poolUsage;
                } else {
                    reduceBy = 0;
                }
                canBeFreed += reduceBy;
                //                logger.info(String.format("Service: %s Type: HYSTRIX Pool: %s Core: %s Usage: %s Free: %s",
                //                        serviceName, pool, corePool, poolUsage, reduceBy));
            }
            //TODO In the email, all stats should be sent. Corepool, poolUsage and other metrics and suggested reduction in poolSize
            logger.info(String.format("Service: %s Type: HYSTRIX Total: %s Free: %s", serviceName, totalCorePool, canBeFreed));
        }
    }

    private List<Integer> hystrixPoolsCore(String serviceName, List<String> hystrixPools) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            String poolCoreQuery = String.format(CORE_POOL_QUERY, serviceName, hystrixPool);
            queries.add(poolCoreQuery);
        }
        List<Integer> responses;
        try {
            responses = executeGrafanaQueries(queries);
            if(CollectionUtils.isEmpty(responses)) {
                logger.error("Error in getting pool core grafana response. Got grafanaResponse = []");
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("Error in running pool core grafana queries: " + e.getMessage(), e);
            return Collections.emptyList();
        }
        return responses;
    }

    private List<Integer> hystrixPoolsUsage(String serviceName, List<String> hystrixPools) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            String poolUsageQuery = String.format(POOL_USAGE_QUERY, PERCENTILE, serviceName, hystrixPool);
            queries.add(poolUsageQuery);
        }
        List<Integer> responses;
        try {
            responses = executeGrafanaQueries(queries);
            if(CollectionUtils.isEmpty(responses)) {
                logger.error("Error in getting pool core grafana response. Got grafanaResponse = []");
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("Error in running pool core grafana queries: " + e.getMessage(), e);
            return Collections.emptyList();
        }
        return responses;
    }

    private int getValueFromGrafanaResponse(String response) {
        JSONObject jsonObject = new JSONObject(response);
        JSONArray seriesJSONArray = getArrayFromJSONObject(jsonObject, SERIES);
        JSONObject seriesJSONObject = getObjectFromJSONArray(seriesJSONArray, INDEX_ZERO);
        JSONArray valuesJSONArray = getArrayFromJSONObject(seriesJSONObject, VALUES);
        valuesJSONArray = getArrayFromJSONArray(valuesJSONArray, INDEX_ZERO);
        if(valuesJSONArray != null && valuesJSONArray.length() > 1) {
            return (int) valuesJSONArray.get(INDEX_ONE);
        }
        return NULL_VALUE;
    }

    private List<Integer> executeGrafanaQueries(List<String> queries) throws Exception {
        List<Integer> results = new ArrayList<>();
        List<HttpResponse> httpResponses = grafanaService.runGrafanaQueries(queries);
        if(httpResponses == null || CollectionUtils.isEmpty(httpResponses)) {
            return Collections.emptyList();
        }
        for(HttpResponse httpResponse : CollectionUtils.nullAndEmptySafeValueList(httpResponses)) {
            int status = httpResponse.getStatusLine().getStatusCode();
            if (status < STATUS_OK_RANGE_START || status >= STATUS_OK_RANGE_END) {
                logger.error("Error in Http get, Status Code: " + httpResponse.getStatusLine().getStatusCode() + " received Response: " + httpResponse);
                return Collections.emptyList();
            }
            String data = EntityUtils.toString(httpResponse.getEntity());
            JSONObject jsonObject = new JSONObject(data);
            if(jsonObject.has(RESULTS)) {
                ((JSONArray) jsonObject.get(RESULTS)).forEach(e -> results.add(getValueFromGrafanaResponse(e.toString())));
            }
        }
        return results;
    }

    private List<String> getHystrixPoolList(String serviceName) throws Exception {
        List<String> poolNames = new ArrayList<>();
        String hystrixPoolList = String.format(HYSTRIX_POOL_LIST_QUERY, serviceName);
        String query = String.format(QUERY, hystrixPoolList);

        HttpResponse response = getHttpResponse(client, query);
        if(response == null) {
            return Collections.emptyList();
        }

        String data = EntityUtils.toString(response.getEntity());
        JSONArray poolJSONArray = getValuesFromMeasurementResponseData(data);
        if(poolJSONArray == null) {
            logger.error("Error in getting value from data: " + data);
            return Collections.emptyList();
        }
        //TODO Pool name might not match with service name. Either we need to introduce nomenclature for pool naming
        Pattern pattern = Pattern.compile(HYSTRIX_POOL_NAME_PATTERN);
        for(int i = 0; i < poolJSONArray.length(); i++) {
            String metrics = ((JSONArray)poolJSONArray.get(i)).get(0)
                    .toString();
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
