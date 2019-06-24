package com.optimizer.threadpool;

import com.collections.CollectionUtils;
import com.google.common.collect.Lists;
import com.optimizer.config.GrafanaConfig;
import com.optimizer.config.MailConfig;
import com.optimizer.config.OptimisedConfig;
import com.optimizer.config.ThreadPoolConfig;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.mail.MailSender;
import lombok.Builder;
import lombok.Data;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.optimizer.threadpool.ThreadPoolQueryUtils.MAX_POOL_QUERY_BY_HOST;
import static com.optimizer.threadpool.ThreadPoolQueryUtils.POOL_USAGE_QUERY_BY_HOST;
import static com.optimizer.threadpool.ThreadPoolUtils.getValueFromGrafanaResponseByHost;
import static com.optimizer.util.OptimizerUtils.*;

/***
 Created by nitish.goyal on 28/03/19
 ***/
@Builder
@Data
public class HystrixThreadPoolHostRunnable implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HystrixThreadPoolRunnable.class.getSimpleName());
    private static final String TAGS = "tags";
    private static final String HOST = "host";

    private GrafanaService grafanaService;
    private ThreadPoolConfig threadPoolConfig;
    private MailSender mailSender;
    private Map<String, String> serviceVsOwnerMap;
    private MailConfig mailConfig;
    private GrafanaConfig grafanaConfig;
    private List<String> clusters;

    @Builder
    public HystrixThreadPoolHostRunnable(GrafanaService grafanaService, ThreadPoolConfig threadPoolConfig, MailSender mailSender,
                                         Map<String, String> serviceVsOwnerMap, MailConfig mailConfig, GrafanaConfig grafanaConfig,
                                         List<String> clusters) {
        this.grafanaService = grafanaService;
        this.threadPoolConfig = threadPoolConfig;
        this.mailSender = mailSender;
        this.serviceVsOwnerMap = serviceVsOwnerMap;
        this.mailConfig = mailConfig;
        this.grafanaConfig = grafanaConfig;
        this.clusters = clusters;
    }

    @Override
    public void run() {
        List<OptimisedConfig> optimisedConfigs = Lists.newArrayList();
        for(String cluster : CollectionUtils.nullSafeList(clusters)) {
            Map<String, List<String>> serviceVsPoolList = grafanaService.getServiceVsPoolList(grafanaConfig.getPrefix(), cluster);
            if(CollectionUtils.isEmpty(serviceVsPoolList)) {
                LOGGER.error("Error in getting serviceVsPoolList. Got empty map");
                continue;
            }
            for(String service : CollectionUtils.nullAndEmptySafeValueList(serviceVsPoolList.keySet())) {
                OptimisedConfig optimisedConfig = OptimisedConfig.builder()
                        .cluster(cluster)
                        .service(service)
                        .build();
                handleHystrixPoolByHost(cluster, service, serviceVsPoolList, optimisedConfig);
                optimisedConfigs.add(optimisedConfig);
            }
        }

    }

    private void handleHystrixPoolByHost(String cluster, String serviceName, Map<String, List<String>> serviceVsPoolList,
                                         OptimisedConfig optimisedConfig) {
        List<String> hystrixPools = serviceVsPoolList.get(serviceName);

        for(String hystrixPool : CollectionUtils.nullSafeList(hystrixPools)) {

            optimisedConfig.setPool(hystrixPool);

            Map<String, Long> hostVsMaxPool = executePoolQueryByHost(cluster, hystrixPool, MAX_POOL_QUERY_BY_HOST,
                                                                     ExtractionStrategy.AVERAGE
                                                                    );
            Map<String, Long> hostVsPoolUsage = executePoolQueryByHost(cluster, hystrixPool, POOL_USAGE_QUERY_BY_HOST,
                                                                       ExtractionStrategy.MAX
                                                                      );

            for(Map.Entry<String, Long> entry : CollectionUtils.nullSafeMap(hostVsMaxPool)
                    .entrySet()) {
                String host = entry.getKey();
                optimisedConfig.setHost(host);

                long maxPool = hostVsMaxPool.get(host);
                long poolUsage = hostVsPoolUsage.get(host);

                if(maxPool <= 0 || poolUsage <= 0) {
                    continue;
                }

                long usagePercentage = poolUsage * 100 / maxPool;
                reducePool(optimisedConfig, hystrixPool, maxPool, poolUsage, usagePercentage);
                extendPool(optimisedConfig, hystrixPool, maxPool, poolUsage, usagePercentage);
            }
        }
    }

    private void extendPool(OptimisedConfig optimisedConfig, String hystrixPool, long maxPool, long poolUsage, long usagePercentage) {
        if(usagePercentage > threadPoolConfig.getThresholdMaxUsagePercentage()) {
            long extendBy = poolUsage - ((maxPool * threadPoolConfig.getThresholdMinUsagePercentage()) / 100);
            if(extendBy > 0) {
                optimisedConfig.setOptimisedPoolValue((int)(maxPool + extendBy));
                LOGGER.info("Hystrix Pool: {} Max:{}, Usage: {}, Extend By: {} ", hystrixPool, maxPool, poolUsage, extendBy);
            }
        }
    }

    private void reducePool(OptimisedConfig optimisedConfig, String hystrixPool, long maxPool, long poolUsage, long usagePercentage) {
        if(usagePercentage < threadPoolConfig.getThresholdMinUsagePercentage()) {
            long reduceBy = ((maxPool * threadPoolConfig.getThresholdMinUsagePercentage()) / 100) - poolUsage;
            if(reduceBy > threadPoolConfig.getReduceByThreshold()) {
                optimisedConfig.setOptimisedPoolValue((int)(maxPool - reduceBy));
                LOGGER.info("Hystrix Pool: {} Max: {}, Usage: {}, Reduce By: {}", hystrixPool, maxPool, poolUsage, reduceBy);
            }
        }
    }

    private Map<String, Long> executePoolQueryByHost(String cluster, String hystrixPool, String query,
                                                     ExtractionStrategy extractionStrategy) {

        try {
            String poolQuery = String.format(query, grafanaConfig.getPrefix(), cluster, hystrixPool,
                                             Integer.toString(threadPoolConfig.getQueryDurationInHours())
                                            );
            return executeGrafanaQueriesByHost(poolQuery, extractionStrategy);
        } catch (Exception e) {
            LOGGER.error("Error in executing grafana queries: " + e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private Map<String, Long> executeGrafanaQueriesByHost(String query, ExtractionStrategy extractionStrategy) throws Exception {
        Map<String, Long> hostVsResult = new HashMap<>();

        HttpResponse httpResponse = grafanaService.execute(query);

        int status = httpResponse.getStatusLine()
                .getStatusCode();
        if(status < STATUS_OK_RANGE_START || status >= STATUS_OK_RANGE_END) {
            LOGGER.error("Error in Http get, Status Code: {} with response {}", httpResponse.getStatusLine()
                    .getStatusCode(), httpResponse);
            return Collections.emptyMap();
        }
        String data = EntityUtils.toString(httpResponse.getEntity());
        JSONObject jsonObject = new JSONObject(data);
        if(jsonObject.has(RESULTS)) {
            JSONArray resultArray = (JSONArray)jsonObject.get(RESULTS);
            JSONObject resultObject = getObjectFromJSONArray(resultArray, INDEX_ZERO);
            JSONArray seriesJSONArray = getArrayFromJSONObject(resultObject, SERIES);
            for(int index = 0; index < seriesJSONArray.length(); index++) {
                JSONObject seriesObject = (JSONObject)seriesJSONArray.get(index);
                long value = getValueFromGrafanaResponseByHost(seriesObject.toString(), extractionStrategy);
                if(seriesObject.get(TAGS) != null && ((JSONObject)seriesObject.get(TAGS)).get(HOST) != null) {
                    hostVsResult.put((String)((JSONObject)seriesObject.get(TAGS)).get(HOST), value);
                }
            }
        }
        return hostVsResult;
    }


}
