package com.optimizer.threadpool;

import com.collections.CollectionUtils;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.grafana.config.GrafanaConfig;
import com.optimizer.mail.MailSender;
import com.optimizer.mail.config.MailConfig;
import com.optimizer.threadpool.config.ThreadPoolConfig;
import lombok.Builder;
import lombok.Data;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.optimizer.threadpool.ThreadPoolQueryUtils.MAX_POOL_QUERY;
import static com.optimizer.threadpool.ThreadPoolQueryUtils.POOL_USAGE_QUERY;
import static com.optimizer.util.OptimizerUtils.*;

/***
 Created by mudit.g on Feb, 2019
 ***/
@Builder
@Data
public class HystrixThreadPoolService implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HystrixThreadPoolService.class.getSimpleName());
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
    public HystrixThreadPoolService(GrafanaService grafanaService, ThreadPoolConfig threadPoolConfig, MailSender mailSender,
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

        for(String cluster : CollectionUtils.nullSafeList(clusters)) {

            Map<String, List<String>> serviceVsPoolList = grafanaService.getServiceVsPoolList(cluster);

            if(CollectionUtils.isEmpty(serviceVsPoolList)) {
                LOGGER.error("Error in getting serviceVsPoolList. Got empty map");
                continue;
            }
            for(String service : CollectionUtils.nullAndEmptySafeValueList(serviceVsPoolList.keySet())) {
                String ownerEmail = mailConfig.getDefaultOwnersEmails();
                if(serviceVsOwnerMap.containsKey(service)) {
                    ownerEmail = serviceVsOwnerMap.get(service);
                }
                handleHystrixPool(service, serviceVsPoolList, ownerEmail);
            }
        }
    }

    private void handleHystrixPool(String serviceName, Map<String, List<String>> serviceVsPoolList, String ownerEmail) {
        List<String> hystrixPools = serviceVsPoolList.get(serviceName);
        if(CollectionUtils.isEmpty(hystrixPools)) {
            LOGGER.error("Error in getting hystrix pool list for Service: " + serviceName + ". Got hystrixPools = []");
            return;
        }

        Map<String, Integer> hystrixPoolVsMaxPool = executePoolQuery(hystrixPools, MAX_POOL_QUERY, ExtractionStrategy.AVERAGE);
        if(CollectionUtils.isEmpty(hystrixPoolVsMaxPool)) {
            LOGGER.error("Error in getting hystrix pools core list for Service: " + serviceName + ". Got poolsCore = []");
            return;
        }
        Map<String, Integer> hystrixPoolVsPoolsUsage = executePoolQuery(hystrixPools, POOL_USAGE_QUERY, ExtractionStrategy.MAX);
        if(CollectionUtils.isEmpty(hystrixPoolVsPoolsUsage)) {
            LOGGER.error("Error in getting hystrix pools usage list for Service: " + serviceName + ". Got poolsUsage = []");
            return;
        }
        String pool;
        int maxPool;
        int poolUsage;
        for(String hystrixPool : hystrixPools) {
            int reduceBy = 0;
            int extendBy = 0;
            pool = hystrixPool;
            if(hystrixPoolVsMaxPool.containsKey(pool)) {
                maxPool = hystrixPoolVsMaxPool.get(pool);
            } else {
                LOGGER.error(String.format("Pool: %s, not present in hystrixPoolVsMaxPool map", pool));
                continue;
            }
            if(hystrixPoolVsPoolsUsage.containsKey(pool)) {
                poolUsage = hystrixPoolVsPoolsUsage.get(pool);
            } else {
                LOGGER.error(String.format("Pool: %s, not present in hystrixPoolVsPoolsUsage map", pool));
                continue;
            }
            if(maxPool <= 0 || poolUsage <= 0) {
                continue;
            }
            int usagePercentage = poolUsage * 100 / maxPool;
            if(usagePercentage < threadPoolConfig.getThresholdMinUsagePercentage()) {
                reduceBy = ((maxPool * threadPoolConfig.getThresholdMinUsagePercentage()) / 100) - poolUsage;
                if(reduceBy > threadPoolConfig.getReduceByThreshold()) {
                    mailSender.send(MAIL_SUBJECT, getReduceByMailBody(serviceName, pool, maxPool, poolUsage, reduceBy, ownerEmail),
                                    ownerEmail
                                   );
                }
            }
            if(usagePercentage > threadPoolConfig.getThresholdMaxUsagePercentage()) {
                extendBy = poolUsage - ((maxPool * threadPoolConfig.getThresholdMinUsagePercentage()) / 100);
                if(extendBy > threadPoolConfig.getExtendByThreshold()) {
                    mailSender.send(MAIL_SUBJECT, getExtendByMailBody(serviceName, pool, maxPool, poolUsage, extendBy, ownerEmail),
                                    ownerEmail
                                   );
                }
            }
            LOGGER.info(
                    String.format("Service: %s Type: HYSTRIX Pool: %s Max: %s Usage: %s Reduce: %s Extend: %s", serviceName, pool, maxPool,
                                  poolUsage, reduceBy, extendBy
                                 ));
        }
    }

    private Map<String, Integer> executePoolQuery(List<String> hystrixPools, String query, ExtractionStrategy extractionStrategy) {
        List<String> queries = new ArrayList<>();
        for(String hystrixPool : CollectionUtils.nullAndEmptySafeValueList(hystrixPools)) {
            String poolQuery = String.format(query, grafanaConfig.getPrefix(), hystrixPool,
                                             Integer.toString(threadPoolConfig.getQueryDurationInHours())
                                            );
            queries.add(poolQuery);
        }
        Map<String, Integer> responses;
        try {
            responses = executeGrafanaQueries(queries, hystrixPools, extractionStrategy);
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

    private Map<String, Integer> executeGrafanaQueries(List<String> queries, List<String> hystrixPools,
                                                       ExtractionStrategy extractionStrategy) throws Exception {
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
                    int result = ThreadPoolUtils.getValueFromGrafanaResponse(resultArray.get(resultIndex)
                                                                     .toString(), extractionStrategy);
                    hystrixPoolVsResult.put(hystrixPools.get(hystrixPoolIndex), result);
                    hystrixPoolIndex++;
                }
            }
        }
        return hystrixPoolVsResult;
    }



}
