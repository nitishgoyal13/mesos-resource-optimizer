package com.optimizer.mesosmonitor;

import com.collections.CollectionUtils;
import com.google.common.collect.Lists;
import com.optimizer.config.GrafanaConfig;
import com.optimizer.config.MailConfig;
import com.optimizer.config.MesosMonitorConfig;
import com.optimizer.config.ThresholdParams;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.mail.MailSender;
import com.optimizer.response.AppOptimizationResource;
import com.optimizer.response.MesosOptimizationResponse;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.optimizer.mesosmonitor.MesosMonitorQueryUtils.*;
import static com.optimizer.util.OptimizerUtils.ExtractionStrategy;
import static com.optimizer.util.OptimizerUtils.MAIL_SUBJECT;

/***
 Created by nitish.goyal on June, 2019
 ***/
public class MesosMonitorRunnable implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MesosMonitorRunnable.class.getSimpleName());
    private static final int SUB_LIST_SIZE = 5;

    private GrafanaService grafanaService;
    private MesosMonitorConfig mesosMonitorConfig;
    private MailSender mailSender;
    private MailConfig mailConfig;
    private Map<String, String> appVsOwnerMap;
    private GrafanaConfig grafanaConfig;

    @Builder
    public MesosMonitorRunnable(GrafanaService grafanaService, MesosMonitorConfig mesosMonitorConfig, MailSender mailSender,
                                MailConfig mailConfig, Map<String, String> appVsOwnerMap, GrafanaConfig grafanaConfig) {
        this.grafanaService = grafanaService;
        this.mesosMonitorConfig = mesosMonitorConfig;
        this.mailSender = mailSender;
        this.mailConfig = mailConfig;
        this.appVsOwnerMap = appVsOwnerMap;
        this.grafanaConfig = grafanaConfig;
    }

    @Override
    public void run() {
        MesosOptimizationResponse mesosOptimizationResponse = MesosOptimizationResponse.builder()
                .appsOptimizedList(Lists.newArrayList())
                .build();
        List<String> apps = grafanaService.getAppList(grafanaConfig.getPrefix());
        if (CollectionUtils.isEmpty(apps)) {
            LOGGER.error("Error in getting apps. Got empty list");
            return;
        }
        handleMesosResources(apps, mesosOptimizationResponse);
        mailSender.send("Optimize Mesos Resources", getMailBody(mesosOptimizationResponse), mailConfig.getDefaultOwnersEmails());
    }

    private void handleMesosResources(List<String> apps, MesosOptimizationResponse mesosOptimizationResponse) {
        List<List<String>> appsLists = CollectionUtils.partition(apps, SUB_LIST_SIZE);
        for (List<String> appsSubList : appsLists) {

            optimizeResourceUsage(appsSubList, TOTAL_CPU, USED_CPU, mesosMonitorConfig.getCpuThresholds(), ResourcesOptimized.CPU,
                    mesosOptimizationResponse
            );

            optimizeResourceUsage(appsSubList, TOTAL_MEMORY, USED_MEMORY, mesosMonitorConfig.getMemoryThresholds(),
                    ResourcesOptimized.MEMORY, mesosOptimizationResponse
            );

        }
    }

    private void optimizeResourceUsage(List<String> appsSubList, String totalResource, String usedResource, ThresholdParams thresholdParams,
                                       ResourcesOptimized resourcesOptimized, MesosOptimizationResponse mesosOptimizationResponse) {

        if (!thresholdParams.isEnabled()) {
            return;
        }
        Map<String, Long> appVsTotalResources = executeMesosMonitorQuery(appsSubList, totalResource,
                mesosMonitorConfig.getExtractionStrategy()
        );

        Map<String, Long> appVsUsedResources = executeMesosMonitorQuery(appsSubList, usedResource,
                mesosMonitorConfig.getExtractionStrategy()
        );
        if (CollectionUtils.isEmpty(appVsUsedResources) || CollectionUtils.isEmpty(appVsTotalResources)) {
            LOGGER.error("Error in getting resources information");
            return;
        }

        for (String app : appsSubList) {
            long totalRes = appVsTotalResources.get(app);
            long usedRes = appVsUsedResources.get(app);

            String ownerEmail = mailConfig.getDefaultOwnersEmails();
            if (appVsOwnerMap.containsKey(app)) {
                ownerEmail = appVsOwnerMap.get(app);
            }
            if (totalRes > 0 && usedRes > 0) {
                long usagePercentage = usedRes * 100 / totalRes;
                if (usagePercentage < thresholdParams.getMinResourcePercentage()) {
                    reduceResourceUsage(app, totalRes, usedRes, ownerEmail, thresholdParams, resourcesOptimized, mesosOptimizationResponse);
                } else if (usagePercentage > thresholdParams.getMaxResourcePercentage()) {
                    increaseResourceUsage(app, totalRes, usedRes, ownerEmail, thresholdParams, resourcesOptimized,
                            mesosOptimizationResponse
                    );
                }
            }

        }

    }

    private void increaseResourceUsage(String app, long totalRes, long usedRes, String ownerEmail, ThresholdParams thresholdParams,
                                       ResourcesOptimized resourcesOptimized, MesosOptimizationResponse mesosOptimizationResponse) {

        long extendBy = usedRes - ((totalRes * thresholdParams.getMinResourcePercentage()) / 100);
        if (extendBy > thresholdParams.getExtendThreshold()) {
            LOGGER.info("App: {} Total Resource: {} Used Resource: {} Extend: {}", app, totalRes, usedRes, extendBy);
            AppOptimizationResource appOptimizationResource = AppOptimizationResource.builder()
                    .app(app)
                    .extendBy(extendBy)
                    .resourcesOptimized(resourcesOptimized)
                    .allocated(totalRes)
                    .used(usedRes)
                    .build();
            mesosOptimizationResponse.getAppsOptimizedList()
                    .add(appOptimizationResource);
            mailSender.send(MAIL_SUBJECT,
                    getExtendByMailBody(app, totalRes, usedRes, extendBy, ownerEmail, thresholdParams.getMinResourcePercentage(),
                            resourcesOptimized.name()
                    ), ownerEmail
            );
        }
    }

    private void reduceResourceUsage(String app, long totalRes, long usedRes, String ownerEmail, ThresholdParams thresholdParams,
                                     ResourcesOptimized resourcesOptimized, MesosOptimizationResponse mesosOptimizationResponse) {

        long reduceBy = ((totalRes * thresholdParams.getMinResourcePercentage()) / 100) - usedRes;
        if (reduceBy > thresholdParams.getReduceThreshold()) {
            AppOptimizationResource appOptimizationResource = AppOptimizationResource.builder()
                    .app(app)
                    .reduceBy(reduceBy)
                    .resourcesOptimized(resourcesOptimized)
                    .allocated(totalRes)
                    .used(usedRes)
                    .build();
            mesosOptimizationResponse.getAppsOptimizedList()
                    .add(appOptimizationResource);
            LOGGER.info("App: {} Total Resource: {} Used Resource: {} Reduce: {}", app, totalRes, usedRes, reduceBy);
            mailSender.send(MAIL_SUBJECT,
                    getReduceByMailBody(app, totalRes, usedRes, reduceBy, ownerEmail, thresholdParams.getMinResourcePercentage(),
                            resourcesOptimized.name()
                    ), ownerEmail
            );
        }
    }

    private Map<String, Long> executeMesosMonitorQuery(List<String> apps, String metricName, ExtractionStrategy extractionStrategy) {
        List<String> queries = new ArrayList<>();
        for (String app : CollectionUtils.nullAndEmptySafeValueList(apps)) {
            String resourceQuery = String.format(APP_QUERY, grafanaConfig.getPrefix(), app, metricName,
                    Integer.toString(mesosMonitorConfig.getQueryDurationInHours())
            );
            queries.add(resourceQuery);
        }
        Map<String, Long> responses;
        try {
            responses = grafanaService.executeQueriesAndGetMapWithEntity(queries, apps, extractionStrategy);
            if (CollectionUtils.isEmpty(responses)) {
                LOGGER.error("Error in executing grafana queries. Got grafanaResponse = []");
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            LOGGER.error("Error in executing grafana queries: " + e.getMessage(), e);
            return Collections.emptyMap();
        }
        return responses;
    }

    private String getReduceByMailBody(String app, long totalResource, long usedResource, long reduceBy, String ownerEmail,
                                       int threshodMinUsagePercentage, String entityToBeOptimized) {
        return String.format("Hi, %s <br> App %s can be optimized. %s usage is consistently below %s%% in last 8 days. " +
                        " <br>App: %s  <br> Total %s: %s <br> Used %s: %s <br> Can be reduced by: %s " +
                        " <br> Kindly reach out to Nitish for any queries. If you aren't " +
                        "the service owner for the mail received, kindly help me out figuring the service owner", ownerEmail,
                entityToBeOptimized, entityToBeOptimized, Integer.toString(threshodMinUsagePercentage), app,
                entityToBeOptimized, totalResource, entityToBeOptimized, usedResource, reduceBy
        );
    }

    private String getExtendByMailBody(String app, long totalResource, long usedResource, long reduceBy, String ownerEmail,
                                       int threshodMaxUsagePercentage, String entityToBeOptimized) {
        return String.format("Hi, %s <br> App %s can be optimized. %s usage is consistently above %s%% in last 8 days. " +
                        " <br>App: %s  <br> Total %s: %s <br> Used %s: %s <br> Can be extended by: %s " +
                        " <br> Kindly reach out to Nitish for any queries. If you aren't " +
                        "the service owner for the mail received, kindly help me out figuring the service owner", ownerEmail,
                entityToBeOptimized, entityToBeOptimized, Integer.toString(threshodMaxUsagePercentage), app,
                entityToBeOptimized, totalResource, entityToBeOptimized, usedResource, reduceBy
        );
    }

    private String getMailBody(MesosOptimizationResponse mesosOptimizationResponse) {
        StringBuilder sb = new StringBuilder();
        String limiter = ", %s";
        for (AppOptimizationResource appOptimizationResource : mesosOptimizationResponse.getAppsOptimizedList()) {
            sb.append(appOptimizationResource.getApp());
            sb.append(String.format(limiter, appOptimizationResource.getAllocated()));
            sb.append(String.format(limiter, appOptimizationResource.getUsed()));
            sb.append(String.format(limiter, appOptimizationResource.getExtendBy()));
            sb.append(String.format(limiter, appOptimizationResource.getReduceBy()));
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }


    public enum ResourcesOptimized {
        CPU,
        MEMORY
    }
}
