package com.optimizer.threadpool;

import com.collections.CollectionUtils;
import com.optimizer.config.ServiceConfig;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.mail.MailSender;
import com.optimizer.threadpool.config.ThreadPoolConfig;
import org.apache.http.HttpResponse;
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
public class HystrixThreadPoolService extends TimerTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(HystrixThreadPoolService.class.getSimpleName());
    private static final String CLUSTER_NAME = "api";

    private GrafanaService grafanaService;
    private ThreadPoolConfig threadPoolConfig;
    private List<ServiceConfig> serviceConfigs;
    private MailSender mailSender;

    public HystrixThreadPoolService(GrafanaService grafanaService, ThreadPoolConfig threadPoolConfig, MailSender mailSender,
                                    List<ServiceConfig> serviceConfigs) {
        this.grafanaService = grafanaService;
        this.threadPoolConfig = threadPoolConfig;
        this.mailSender = mailSender;
        this.serviceConfigs = serviceConfigs;
    }

    @Override
    public void run() {
        Map<String, List<String>> serviceVsPoolList = grafanaService.getServiceVsPoolList(CLUSTER_NAME);
        if(CollectionUtils.isEmpty(serviceVsPoolList)) {
            LOGGER.error("Error in getting serviceVsPoolList. Got empty map");
            return;
        }
        serviceConfigs.forEach(serviceConfig -> {
            if(serviceVsPoolList.containsKey(serviceConfig.getService())) {
                handleHystrixPool(serviceConfig.getService(), serviceVsPoolList, serviceConfig.getOwnerEmail());
            }
        });
    }

    private void handleHystrixPool(String serviceName, Map<String, List<String>> serviceVsPoolList, String ownerEmail) {
        List<String> hystrixPools = serviceVsPoolList.get(serviceName);
        if(CollectionUtils.isEmpty(hystrixPools)) {
            LOGGER.error("Error in getting hystrix pool list for Service: " + serviceName + ". Got hystrixPools = []");
            return;
        }

        Map<String, Integer> hystrixPoolVsCorePool = executePoolQuery(hystrixPools, CORE_POOL_QUERY, CLUSTER_NAME);
        if(CollectionUtils.isEmpty(hystrixPoolVsCorePool)) {
            LOGGER.error("Error in getting hystrix pools core list for Service: " + serviceName + ". Got poolsCore = []");
            return;
        }
        Map<String, Integer> hystrixPoolVsPoolsUsage = executePoolQuery(hystrixPools, POOL_USAGE_QUERY, CLUSTER_NAME);
        if(CollectionUtils.isEmpty(hystrixPoolVsPoolsUsage)) {
            LOGGER.error("Error in getting hystrix pools usage list for Service: " + serviceName + ". Got poolsUsage = []");
            return;
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
                reduceBy = ((corePool * threadPoolConfig.getThresholdUsagePercentage()) / 100) - poolUsage;
                mailSender.send(MAIL_SUBJECT, getMailBody(serviceName, pool, corePool, poolUsage, reduceBy), ownerEmail);
            }
            canBeFreed += reduceBy;
            LOGGER.info(
                    String.format("Service: %s Type: HYSTRIX Pool: %s Core: %s Usage: %s Free: %s", serviceName, pool, corePool, poolUsage,
                                  reduceBy
                                 ));
        }
        LOGGER.info(String.format("Service: %s Type: HYSTRIX Total: %s Free: %s", serviceName, totalCorePool, canBeFreed));
    }

    private Map<String, Integer> executePoolQuery(List<String> hystrixPools, String query, String clusterName) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            String poolQuery = String.format(query, clusterName, hystrixPool, Integer.toString(threadPoolConfig.getQueryDurationInHours()));
            queries.add(poolQuery);
        }
        Map<String, Integer> responses;
        try {
            responses = executeGrafanaQueries(queries, hystrixPools);
            if(CollectionUtils.isEmpty(responses)) {
                LOGGER.error("Error in executing grafana queries. Got grafanaResponse = []");
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            LOGGER.error("Error in executing grafana queries: " + e.getMessage(), e);
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
