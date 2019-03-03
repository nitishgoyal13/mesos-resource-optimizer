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

import static com.optimizer.threadpool.ThreadPoolQueryUtils.CORE_POOL_QUERY;
import static com.optimizer.threadpool.ThreadPoolQueryUtils.POOL_USAGE_QUERY;
import static com.optimizer.util.OptimizerUtils.*;

/***
 Created by mudit.g on Feb, 2019
 ***/
@Builder
public class HystrixThreadPoolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HystrixThreadPoolService.class.getSimpleName());
    private static final String CLUSTER_NAME = "api";

    private HttpClient client;
    private GrafanaService grafanaService;
    private ThreadPoolConfig threadPoolConfig;

    public HystrixThreadPoolService(HttpClient client, GrafanaService grafanaService, ThreadPoolConfig threadPoolConfig) {
        this.client = client;
        this.grafanaService = grafanaService;
        this.threadPoolConfig = threadPoolConfig;
    }

    public void handleHystrixPools() {
        Map<String, List<String>> serviceVsPoolList = grafanaService.getServiceVsPoolList(CLUSTER_NAME);
        if(CollectionUtils.isEmpty(serviceVsPoolList)) {
            LOGGER.error("Error in getting serviceVsPoolList. Got empty map");
            return;
        }
        for(String serviceName : CollectionUtils.nullAndEmptySafeValueList(serviceVsPoolList.keySet())) {
            List<String> hystrixPools = serviceVsPoolList.get(serviceName);
            if(CollectionUtils.isEmpty(hystrixPools)) {
                LOGGER.error("Error in getting hystrix pool list for Service: " + serviceName + ". Got hystrixPools = []");
                continue;
            }

            Map<String, Integer> hystrixPoolVsCorePool = corePool(hystrixPools);
            if(CollectionUtils.isEmpty(hystrixPoolVsCorePool)) {
                LOGGER.error("Error in getting hystrix pools core list for Service: " + serviceName + ". Got poolsCore = []");
                continue;
            }
            Map<String, Integer> hystrixPoolVsPoolsUsage = poolUsage(hystrixPools);
            if(CollectionUtils.isEmpty(hystrixPoolVsPoolsUsage)) {
                LOGGER.error("Error in getting hystrix pools usage list for Service: " + serviceName + ". Got poolsUsage = []");
                continue;
            }
            String pool;
            int corePool;
            int poolUsage;
            int totalCorePool = 0;
            int canBeFreed = 0;
            for(String hystrixPool : hystrixPools) {
                int reduceBy = 0;
                pool = hystrixPool;
                if(hystrixPoolVsCorePool.containsKey(pool)) {
                    corePool = hystrixPoolVsCorePool.get(pool);
                } else {
                    LOGGER.error(String.format("Pool: %s, not present in hystrixPoolVsCorePool map", pool));
                    continue;
                }
                if(hystrixPoolVsPoolsUsage.containsKey(pool)) {
                    poolUsage = hystrixPoolVsPoolsUsage.get(pool);
                } else {
                    LOGGER.error(String.format("Pool: %s, not present in hystrixPoolVsPoolsUsage map", pool));
                    continue;
                }
                if(corePool <= 0 || poolUsage <= 0) {
                    continue;
                }
                totalCorePool += corePool;
                int usagePercentage = poolUsage * 100 / corePool;
                if(usagePercentage < threadPoolConfig.getThresholdUsagePercentage()) {
                    reduceBy = ((corePool * threadPoolConfig.getMaxUsagePercentage()) / 100) - poolUsage;
                }
                canBeFreed += reduceBy;
                LOGGER.info(String.format("Service: %s Type: HYSTRIX Pool: %s Core: %s Usage: %s Free: %s", serviceName, pool, corePool,
                                          poolUsage, reduceBy
                                         ));
            }
            //TODO In the email, all stats should be sent. Corepool, poolUsage and other metrics and suggested reduction in poolSize
            LOGGER.info(String.format("Service: %s Type: HYSTRIX Total: %s Free: %s", serviceName, totalCorePool, canBeFreed));
        }
    }

    private Map<String, Integer> corePool(List<String> hystrixPools) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            String corePoolQuery = String.format(CORE_POOL_QUERY, CLUSTER_NAME, hystrixPool,
                                                 Integer.toString(threadPoolConfig.getQueryDurationInHours())
                                                );
            queries.add(corePoolQuery);
        }
        Map<String, Integer> responses;
        try {
            responses = executeGrafanaQueries(queries, hystrixPools);
            if(CollectionUtils.isEmpty(responses)) {
                LOGGER.error("Error in getting pool core grafana response. Got grafanaResponse = []");
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            LOGGER.error("Error in running pool core grafana queries: " + e.getMessage(), e);
            return Collections.emptyMap();
        }
        return responses;
    }

    private Map<String, Integer> poolUsage(List<String> hystrixPools) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            String poolUsageQuery = String.format(POOL_USAGE_QUERY, CLUSTER_NAME, hystrixPool,
                                                  Integer.toString(threadPoolConfig.getQueryDurationInHours())
                                                 );
            queries.add(poolUsageQuery);
        }
        Map<String, Integer> responses;
        try {
            responses = executeGrafanaQueries(queries, hystrixPools);
            if(CollectionUtils.isEmpty(responses)) {
                LOGGER.error("Error in getting pool core grafana response. Got grafanaResponse = []");
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            LOGGER.error("Error in running pool core grafana queries: " + e.getMessage(), e);
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
        for(HttpResponse httpResponse : httpResponses) {
            int status = httpResponse.getStatusLine()
                    .getStatusCode();
            if(status < STATUS_OK_RANGE_START || status >= STATUS_OK_RANGE_END) {
                LOGGER.error("Error in Http get, Status Code: " + httpResponse.getStatusLine()
                        .getStatusCode() + " received Response: " + httpResponse);
                return Collections.emptyMap();
            }
            String data = EntityUtils.toString(httpResponse.getEntity());
            JSONObject jsonObject = new JSONObject(data);
            if(jsonObject.has(RESULTS)) {
                JSONArray resultArray = (JSONArray)jsonObject.get(RESULTS);
                for(int resultIndex = 0; resultIndex < resultArray.length(); resultIndex++) {
                    int result = getValueFromGrafanaResponse(resultArray.get(resultIndex)
                                                                     .toString());
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
        if(valuesJSONArray != null && valuesJSONArray.length() > 1 && valuesJSONArray.get(INDEX_ONE) instanceof Integer) {
            return (int)valuesJSONArray.get(INDEX_ONE);
        }
        return NULL_VALUE;
    }

}
