package com.optimizer.mesosmonitor;

import static com.optimizer.mesosmonitor.MesosMonitorQueryUtils.APP_QUERY;
import static com.optimizer.mesosmonitor.MesosMonitorQueryUtils.TOTAL_CPU;
import static com.optimizer.mesosmonitor.MesosMonitorQueryUtils.TOTAL_MEMORY;
import static com.optimizer.mesosmonitor.MesosMonitorQueryUtils.USED_CPU;
import static com.optimizer.mesosmonitor.MesosMonitorQueryUtils.USED_MEMORY;
import static com.optimizer.util.OptimizerUtils.ExtractionStrategy;

import com.collections.CollectionUtils;
import com.google.common.collect.Lists;
import com.optimizer.config.GrafanaConfig;
import com.optimizer.config.MailConfig;
import com.optimizer.config.MesosMonitorConfig;
import com.optimizer.config.ThresholdParams;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.mail.MailSender;
import com.optimizer.response.AppOptimizationResponse;
import com.optimizer.response.MesosOptimizationResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 Created by nitish.goyal on June, 2019
 ***/
@Slf4j
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
    public MesosMonitorRunnable(GrafanaService grafanaService, MesosMonitorConfig mesosMonitorConfig,
            MailSender mailSender,
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
        log.info("Running mesos resource optimizer");
        MesosOptimizationResponse mesosOptimizationResponse = MesosOptimizationResponse.builder()
                .appsOptimizedList(Lists.newArrayList())
                .build();
        List<String> apps = grafanaService.getAppList();
        if (CollectionUtils.isEmpty(apps)) {
            LOGGER.error("Error in getting apps. Got empty list");
            return;
        }
        handleMesosResources(apps, mesosOptimizationResponse);
        mesosOptimizationResponse.getAppsOptimizedList().sort((o1, o2) -> {
            return o2.getReduceBy() > o1.getReduceBy() ? 1 : -1;
        });
        mailSender.send("Optimize Mesos Resources", getMailBody(mesosOptimizationResponse),
                mailConfig.getDefaultOwnersEmails());
    }

    private void handleMesosResources(List<String> apps, MesosOptimizationResponse mesosOptimizationResponse) {
        List<List<String>> appsLists = CollectionUtils.partition(apps, SUB_LIST_SIZE);
        for (List<String> appsSubList : appsLists) {

            optimizeResourceUsage(appsSubList, TOTAL_CPU, USED_CPU, mesosMonitorConfig.getCpuThresholds(),
                    ResourcesOptimized.CPU,
                    mesosOptimizationResponse
            );

            optimizeResourceUsage(appsSubList, TOTAL_MEMORY, USED_MEMORY, mesosMonitorConfig.getMemoryThresholds(),
                    ResourcesOptimized.MEMORY, mesosOptimizationResponse
            );

        }
    }

    private void optimizeResourceUsage(List<String> appsSubList, String totalResource, String usedResource,
            ThresholdParams thresholdParams,
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

            StringBuilder sb = new StringBuilder();
            sb.append(mailConfig.getDefaultOwnersEmails());
            if (appVsOwnerMap.containsKey(app)) {
                sb.append(",");
                sb.append(appVsOwnerMap.get(app));
            }
            log.info("Email for app {}, email : {} ", app, sb.toString());
            if (totalRes > 0 && usedRes >= 0) {
                long usagePercentage = usedRes * 100 / totalRes;
                if (usagePercentage < thresholdParams.getMinResourcePercentage()) {
                    reduceResourceUsage(app, totalRes, usedRes, sb.toString(), thresholdParams, resourcesOptimized,
                            mesosOptimizationResponse);
                } else if (usagePercentage > thresholdParams.getMaxResourcePercentage()) {
                    increaseResourceUsage(app, totalRes, usedRes, sb.toString(), thresholdParams, resourcesOptimized,
                            mesosOptimizationResponse);
                }
            }

        }

    }

    private void increaseResourceUsage(String app, long totalRes, long usedRes, String ownerEmail,
            ThresholdParams thresholdParams,
            ResourcesOptimized resourcesOptimized, MesosOptimizationResponse mesosOptimizationResponse) {

        long extendBy = (totalRes * thresholdParams.getExtendThresholdPercentage() / 100) - usedRes;
        if (extendBy > thresholdParams.getExtendThreshold()) {
            LOGGER.info("App: {} Total Resource: {} Used Resource: {} Extend: {} Email : {}", app, totalRes, usedRes,
                    extendBy, ownerEmail);
            AppOptimizationResponse appOptimizationResponse = AppOptimizationResponse.builder()
                    .app(app)
                    .extendBy(extendBy)
                    .resourcesOptimized(resourcesOptimized)
                    .allocated(totalRes)
                    .used(usedRes)
                    .build();
            mesosOptimizationResponse.getAppsOptimizedList()
                    .add(appOptimizationResponse);
            /*mailSender.send(MAIL_SUBJECT,
                    getExtendByMailBody(app, totalRes, usedRes, extendBy, ownerEmail,
                            thresholdParams.getMinResourcePercentage(),
                            resourcesOptimized.name()
                    ), ownerEmail
            );*/
        }
    }

    private void reduceResourceUsage(String app, long totalRes, long usedRes, String ownerEmail,
            ThresholdParams thresholdParams,
            ResourcesOptimized resourcesOptimized, MesosOptimizationResponse mesosOptimizationResponse) {

        long reduceBy = totalRes  - usedRes * thresholdParams.getMaxResourcesAllocatedPercentage() / 100;
        if (usedRes == 0){
            reduceBy = totalRes - thresholdParams.getDefaultResources();
        }
        if (reduceBy > thresholdParams.getReduceThreshold() || usedRes == 0) {
            AppOptimizationResponse appOptimizationResponse = AppOptimizationResponse.builder()
                    .app(app)
                    .reduceBy(reduceBy)
                    .resourcesOptimized(resourcesOptimized)
                    .allocated(totalRes)
                    .used(usedRes)
                    .build();
            mesosOptimizationResponse.getAppsOptimizedList()
                    .add(appOptimizationResponse);
            LOGGER.info("App: {} Total Resource: {} Used Resource: {} Reduce: {} Email {}", app, totalRes, usedRes,
                    reduceBy, ownerEmail);
            /*mailSender.send(MAIL_SUBJECT,
                    getReduceByMailBody(app, totalRes, usedRes, reduceBy, ownerEmail,
                            thresholdParams.getMinResourcePercentage(), resourcesOptimized.name()), ownerEmail);*/
        }
    }

    private Map<String, Long> executeMesosMonitorQuery(List<String> apps, String metricName,
            ExtractionStrategy extractionStrategy) {
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

    private String getReduceByMailBody(String app, long totalResource, long usedResource, long reduceBy,
            String ownerEmail,
            int threshodMinUsagePercentage, String entityToBeOptimized) {
        return String
                .format("@%s <br> %s can be optimized. %s usage is consistently below %s%% in last 8 days. " +
                                " <br>App: %s  <br> Total allocated %s: %s <br> Used %s by application: %s <br>"
                                + " Can be reduced by: %s " +
                                ". Also check your network usage before reducing the number of instances",
                        ownerEmail, entityToBeOptimized, entityToBeOptimized,
                        Integer.toString(threshodMinUsagePercentage), app, entityToBeOptimized, totalResource,
                        entityToBeOptimized, usedResource, reduceBy);
    }

    private String getExtendByMailBody(String app, long totalResource, long usedResource, long reduceBy,
            String ownerEmail,
            int threshodMaxUsagePercentage, String entityToBeOptimized) {
        return String
                .format("@%s <br> %s can be optimized. %s usage is consistently above %s%% in last 8 days. " +
                                " <br>App name: %s  <br> Total Allocated %s: %s <br> Used %s used by application: %s <br>"
                                + "Should be extended by: %s by either increasing resources per instance or increasing "
                                + "number of instances",
                        ownerEmail, entityToBeOptimized, entityToBeOptimized,
                        Integer.toString(threshodMaxUsagePercentage), app, entityToBeOptimized, totalResource,
                        entityToBeOptimized, usedResource, reduceBy);
    }

    private String getMailBody(MesosOptimizationResponse mesosOptimizationResponse) {
        StringBuilder sb = new StringBuilder();
        String formatter = "<td>%s</td>";
        sb.append(
                "<html><body><table cellpadding=\"4\" style=\"border:1px solid #000000;border-collapse:collapse\" border=\"1\">");
        sb.append(
                "<tbody> <tr><th>App</th> <th>Allocated</th> <th>Used</th> <th>Extend By</th> <th>Reduce By</th> </tr>");

        for (AppOptimizationResponse appOptimizationResponse : mesosOptimizationResponse.getAppsOptimizedList()) {
            sb.append(String.format("<tr>%s", String.format(formatter, appOptimizationResponse.getApp())));
            sb.append(String.format(formatter, appOptimizationResponse.getAllocated()));
            sb.append(String.format(formatter, appOptimizationResponse.getUsed()));
            sb.append(String.format(formatter, appOptimizationResponse.getExtendBy()));
            sb.append(String.format("%s</tr>", String.format(formatter, appOptimizationResponse.getReduceBy())));
        }
        sb.append("</tbody></table></body></html>");
        return sb.toString();
    }


    public enum ResourcesOptimized {
        CPU,
        MEMORY
    }
}
