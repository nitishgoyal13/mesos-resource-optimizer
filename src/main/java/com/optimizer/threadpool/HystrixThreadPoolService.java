package com.optimizer.threadpool;

import com.collections.CollectionUtils;
import com.optimizer.config.ThreadPoolConfig;
import com.optimizer.grafana.GrafanaService;
import lombok.Builder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.optimizer.threadpool.ThreadPoolQueryUtils.*;
import static com.optimizer.util.OptimizerUtils.*;

/***
 Created by mudit.g on Feb, 2019
 ***/
@Builder
public class HystrixThreadPoolService {

    private static final Logger logger = LoggerFactory.getLogger(HystrixThreadPoolService.class.getSimpleName());
    private static final String CLUSTER_NAME = "api";

    private HttpClient client;
    private GrafanaService grafanaService;
    private ThreadPoolConfig threadPoolConfig;

    public HystrixThreadPoolService(HttpClient client, GrafanaService grafanaService,
                                    ThreadPoolConfig threadPoolConfig) {
        this.client = client;
        this.grafanaService = grafanaService;
        this.threadPoolConfig = threadPoolConfig;
    }

    public void handleHystrixPools() {
        Map<String, List<String>> serviceVsPoolList = grafanaService.getServiceVsPoolList(CLUSTER_NAME);
        if(CollectionUtils.isEmpty(serviceVsPoolList)) {
            logger.error("Error in getting serviceVsPoolList. Got empty map");
            return;
        }
        for(String serviceName : CollectionUtils.nullAndEmptySafeValueList(serviceVsPoolList.keySet())) {
            List<String> hystrixPools = serviceVsPoolList.get(serviceName);
            if(CollectionUtils.isEmpty(hystrixPools)) {
                logger.error("Error in getting hystrix pool list for Service: " + serviceName + ". Got hystrixPools = []");
                continue;
            }

            Map<String, Integer> hystrixPoolVsCorePool = poolCore(CLUSTER_NAME, hystrixPools);
            if(CollectionUtils.isEmpty(hystrixPoolVsCorePool)) {
                logger.error("Error in getting hystrix pools core list for Service: " + serviceName + ". Got poolsCore = []");
                continue;
            }
            Map<String, Integer> hystrixPoolVsPoolsUsage = poolUsage(CLUSTER_NAME, hystrixPools);
            if(CollectionUtils.isEmpty(hystrixPoolVsPoolsUsage)) {
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
                pool = hystrixPools.get(poolCount);
                if(hystrixPoolVsCorePool.containsKey(pool)) {
                    corePool = hystrixPoolVsCorePool.get(pool);
                } else {
                    logger.error(String.format("Pool: %s, not present in hystrixPoolVsCorePool map"));
                    continue;
                }
                if(hystrixPoolVsPoolsUsage.containsKey(pool)) {
                    poolUsage = hystrixPoolVsPoolsUsage.get(pool);
                } else {
                    logger.error(String.format("Pool: %s, not present in hystrixPoolVsPoolsUsage map"));
                    continue;
                }
                if(corePool <= 0 || poolUsage <= 0) {
                    continue;
                }
                totalCorePool += corePool;
                int usagePercentage = (int)((poolUsage * 100.0f) / corePool);
                if(usagePercentage < threadPoolConfig.getThresholdUsagePercentage()) {
                    reduceBy = (int)((corePool * threadPoolConfig.getMaxUsagePercentage() * 1.0f)/100) - poolUsage;
                } else {
                    reduceBy = 0;
                }
                canBeFreed += reduceBy;
                logger.info(String.format("Service: %s Type: HYSTRIX Pool: %s Core: %s Usage: %s Free: %s",
                                        serviceName, pool, corePool, poolUsage, reduceBy));
            }
            //TODO In the email, all stats should be sent. Corepool, poolUsage and other metrics and suggested reduction in poolSize
            logger.info(String.format("Service: %s Type: HYSTRIX Total: %s Free: %s", serviceName, totalCorePool, canBeFreed));
        }
    }

    private Map<String, Integer> poolCore(String clusterName, List<String> hystrixPools) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            String poolCoreQuery = String.format(CORE_POOL_QUERY, clusterName, hystrixPool,
                    Integer.toString(threadPoolConfig.getQueryDuration()));
            queries.add(poolCoreQuery);
        }
        Map<String, Integer> responses;
        try {
            responses = executeGrafanaQueries(queries, hystrixPools);
            if(CollectionUtils.isEmpty(responses)) {
                logger.error("Error in getting pool core grafana response. Got grafanaResponse = []");
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            logger.error("Error in running pool core grafana queries: " + e.getMessage(), e);
            return Collections.emptyMap();
        }
        return responses;
    }

    private Map<String, Integer> poolUsage(String serviceName, List<String> hystrixPools) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            String poolUsageQuery = String.format(POOL_USAGE_QUERY, serviceName, hystrixPool,
                    Integer.toString(threadPoolConfig.getQueryDuration()));
            queries.add(poolUsageQuery);
        }
        Map<String, Integer> responses;
        try {
            responses = executeGrafanaQueries(queries, hystrixPools);
            if(CollectionUtils.isEmpty(responses)) {
                logger.error("Error in getting pool core grafana response. Got grafanaResponse = []");
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            logger.error("Error in running pool core grafana queries: " + e.getMessage(), e);
            return Collections.emptyMap();
        }
        return responses;
    }

    private Map<String, Integer> executeGrafanaQueries(List<String> queries, List<String> hystrixPools) throws Exception {
        Map<String, Integer> hystrixPoolVsResult = new HashMap<>();
        int hystrixPoolIndex = 0;
        List<HttpResponse> httpResponses = grafanaService.execute(queries);
        if(CollectionUtils.isEmpty(httpResponses)) {
            return Collections.emptyMap();
        }
        for(int index = 0; index < httpResponses.size(); index++) {
            int status = httpResponses.get(index).getStatusLine()
                    .getStatusCode();
            if(status < STATUS_OK_RANGE_START || status >= STATUS_OK_RANGE_END) {
                logger.error("Error in Http get, Status Code: " + httpResponses.get(index).getStatusLine()
                        .getStatusCode() + " received Response: " + httpResponses.get(index));
                return Collections.emptyMap();
            }
            String data = EntityUtils.toString(httpResponses.get(index).getEntity());
            JSONObject jsonObject = new JSONObject(data);
            if(jsonObject.has(RESULTS)) {
                JSONArray resultArray = (JSONArray)jsonObject.get(RESULTS);
                for(int resultIndex = 0; resultIndex < resultArray.length(); resultIndex++) {
                    int result = getValueFromGrafanaResponse(resultArray.get(resultIndex).toString());
                    hystrixPoolVsResult.put(hystrixPools.get(hystrixPoolIndex), result);
                    hystrixPoolIndex++;
                }
            }
        }
        return hystrixPoolVsResult;
    }

    private int getValueFromGrafanaResponse(String response) {
        JSONObject jsonObject = new JSONObject(response);
        JSONArray seriesJSONArray = getArrayFromJSONObject(jsonObject, SERIES);
        JSONObject seriesJSONObject = getObjectFromJSONArray(seriesJSONArray, INDEX_ZERO);
        JSONArray valuesJSONArray = getArrayFromJSONObject(seriesJSONObject, VALUES);
        valuesJSONArray = getArrayFromJSONArray(valuesJSONArray, INDEX_ZERO);
        if(valuesJSONArray != null && valuesJSONArray.length() > 1
                && valuesJSONArray.get(INDEX_ONE) instanceof Integer) {
            return (int)valuesJSONArray.get(INDEX_ONE);
        }
        return NULL_VALUE;
    }

}
