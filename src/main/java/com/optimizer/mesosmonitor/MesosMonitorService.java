package com.optimizer.mesosmonitor;

import com.collections.CollectionUtils;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.mail.MailSender;
import com.optimizer.mail.config.MailConfig;
import com.optimizer.mesosmonitor.config.MesosMonitorConfig;
import lombok.Builder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.optimizer.mesosmonitor.MesosMonitorQueryUtils.*;
import static com.optimizer.util.OptimizerUtils.*;

/***
 Created by mudit.g on Mar, 2019
 ***/
public class MesosMonitorService implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MesosMonitorService.class.getSimpleName());

    private GrafanaService grafanaService;
    private MesosMonitorConfig mesosMonitorConfig;
    private MailSender mailSender;
    private MailConfig mailConfig;
    private Map<String, String> appVsOwnerMap;
    private HttpClient client;

    @Builder
    public MesosMonitorService(GrafanaService grafanaService, MesosMonitorConfig mesosMonitorConfig, MailSender mailSender,
                               MailConfig mailConfig, Map<String, String> appVsOwnerMap, HttpClient client) {
        this.grafanaService = grafanaService;
        this.mesosMonitorConfig = mesosMonitorConfig;
        this.mailSender = mailSender;
        this.mailConfig = mailConfig;
        this.appVsOwnerMap = appVsOwnerMap;
        this.client = client;
    }

    @Override
    public void run() {
        List<String> apps = grafanaService.getAppList(mesosMonitorConfig.getPrefix());
        if(CollectionUtils.isEmpty(apps)) {
            LOGGER.error("Error in getting apps. Got empty list");
            return;
        }
        handleMesosMonitor(apps);
    }

    private void handleMesosMonitor(List<String> apps) {
        Map<String, Integer> appVsTotalCpu = executeMesosMonitorQuery(apps, APP_QUERY, TOTAL_CPU,
                ExtractionStrategy.AVERAGE);
        if(CollectionUtils.isEmpty(appVsTotalCpu)) {
            LOGGER.error("Error in getting apps total CPU.");
            return;
        }
        Map<String, Integer> appVsUsedCpu = executeMesosMonitorQuery(apps, APP_QUERY, USED_CPU,
                ExtractionStrategy.MAX);
        if(CollectionUtils.isEmpty(appVsUsedCpu)) {
            LOGGER.error("Error in getting apps used CPU.");
            return;
        }
        Map<String, Integer> appVsTotalMemory = executeMesosMonitorQuery(apps, APP_QUERY, TOTAL_MEMORY,
                ExtractionStrategy.AVERAGE);
        if(CollectionUtils.isEmpty(appVsTotalMemory)) {
            LOGGER.error("Error in getting apps total Memory.");
            return;
        }
        Map<String, Integer> appVsUsedMemory = executeMesosMonitorQuery(apps, APP_QUERY, USED_MEMORY,
                ExtractionStrategy.MAX);
        if(CollectionUtils.isEmpty(appVsUsedMemory)) {
            LOGGER.error("Error in getting apps used Memory.");
            return;
        }
        int totalCPU;
        int usedCPU;
        int totalMemory;
        int usedMemory;
        for(String app: apps) {
            if(appVsTotalCpu.containsKey(app)) {
                totalCPU = appVsTotalCpu.get(app);
            } else {
                LOGGER.error(String.format("App: %s, not present in appVsTotalCpu map", app));
                continue;
            }
            if(appVsUsedCpu.containsKey(app)) {
                usedCPU = appVsUsedCpu.get(app);
            } else {
                LOGGER.error(String.format("App: %s, not present in appVsUsedCpu map", app));
                continue;
            }
            if(appVsTotalMemory.containsKey(app)) {
                totalMemory = appVsTotalMemory.get(app);
            } else {
                LOGGER.error(String.format("App: %s, not present in appVsTotalMemory map", app));
                continue;
            }
            if(appVsUsedMemory.containsKey(app)) {
                usedMemory = appVsUsedMemory.get(app);
            } else {
                LOGGER.error(String.format("App: %s, not present in appVsUsedMemory map", app));
                continue;
            }
            String appName = getAppName(app);
            String ownerEmail = mailConfig.getDefaultOwnersEmails();
            if(appName != null) {
                if(appVsOwnerMap.containsKey(appName)) {
                    ownerEmail = appVsOwnerMap.get(appName);
                }
            }
            if(totalCPU > 0 && usedCPU > 0) {
                int usagePercentage = usedCPU * 100 / totalCPU;
                if(usagePercentage < mesosMonitorConfig.getThresholdMinCpuUsagePercentage()) {
                    int reduceBy = ((totalCPU * mesosMonitorConfig.getThresholdMinCpuUsagePercentage()) / 100) - usedCPU;
                    if(reduceBy > mesosMonitorConfig.getCpuReduceByThreshold()) {
                        LOGGER.info(String.format("App: %s Total CPU: %s Used CPU: %s Reduce: %s",
                                app, totalCPU, usedCPU, reduceBy
                        ));
//                        mailSender.send(MAIL_SUBJECT, getReduceByMailBody(app, totalCPU, usedCPU, reduceBy, ownerEmail,
//                            mesosMonitorConfig.getThresholdMinCpuUsagePercentage(), "CPU"),
//                                    ownerEmail
//                                   );
                    }
                }
                if(usagePercentage > mesosMonitorConfig.getThresholdMaxCpuUsagePercentage()) {
                    int extendBy = usedCPU - ((totalCPU * mesosMonitorConfig.getThresholdMinCpuUsagePercentage()) / 100);
                    if(extendBy > mesosMonitorConfig.getCpuExtendByThreshold()) {
                        LOGGER.info(String.format("App: %s Total CPU: %s Used CPU: %s Extend: %s",
                                app, totalCPU, usedCPU, extendBy
                        ));
//                        mailSender.send(MAIL_SUBJECT, getExtendByMailBody(app, totalCPU, usedCPU, extendBy, ownerEmail,
//                                mesosMonitorConfig.getThresholdMinCpuUsagePercentage(), "CPU"),
//                                ownerEmail
//                        );
                    }
                }
            }
            if(totalMemory > 0 && usedMemory > 0) {
                int usagePercentage = usedMemory * 100 / totalMemory;
                if(usagePercentage < mesosMonitorConfig.getThresholdMinMemoryUsagePercentage()) {
                    int reduceBy = ((totalMemory * mesosMonitorConfig.getThresholdMinMemoryUsagePercentage()) / 100) - usedMemory;
                    if(reduceBy > mesosMonitorConfig.getMemoryReduceByThreshold()) {
                        LOGGER.info(String.format("App: %s Total Memory: %s Used Memory: %s Reduce: %s",
                                app, totalMemory, usedMemory, reduceBy
                        ));
//                        mailSender.send(MAIL_SUBJECT, getReduceByMailBody(app, totalMemory, usedMemory, reduceBy, ownerEmail,
//                                mesosMonitorConfig.getThresholdMinMemoryUsagePercentage(), "Memory"),
//                                ownerEmail
//                        );
                    }
                }
                if(usagePercentage > mesosMonitorConfig.getThresholdMaxMemoryUsagePercentage()) {
                    int extendBy = usedMemory - ((totalMemory * mesosMonitorConfig.getThresholdMinMemoryUsagePercentage()) / 100);
                    if(extendBy > mesosMonitorConfig.getMemoryExtendByThreshold()) {
                        LOGGER.info(String.format("App: %s Total Memory: %s Used Memory: %s Extend: %s",
                                app, totalMemory, usedMemory, extendBy
                        ));
//                        mailSender.send(MAIL_SUBJECT, getExtendByMailBody(app, totalMemory, usedMemory, extendBy, ownerEmail,
//                                mesosMonitorConfig.getThresholdMaxMemoryUsagePercentage(), "Memory"),
//                                ownerEmail
//                        );
                    }
                }
            }
        }

    }

    private Map<String, Integer> executeMesosMonitorQuery(List<String> apps, String query, String metricName,
                                                          ExtractionStrategy extractionStrategy) {
        List<String> queries = new ArrayList<>();
        for(String app : CollectionUtils.nullAndEmptySafeValueList(apps)) {
            String poolQuery = String.format(query, mesosMonitorConfig.getPrefix(), app, metricName,
                    Integer.toString(mesosMonitorConfig.getQueryDurationInHours()));
            queries.add(poolQuery);
        }
        Map<String, Integer> responses;
        try {
            responses = grafanaService.executeQueriesAndGetMapWithEntity(queries, apps, extractionStrategy);
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

    private String getReduceByMailBody(String app, int totalCPU, int usedCPU, int reduceBy, String ownerEmail,
                                                int threshodMinUsagePercentage, String entityToBeOptimized) {
        return String.format(
                "Hi, %s <br> App %s can be optimized. %s usage is consistently below %s%% in last 8 days. " +
                        " <br>App: %s  <br> Total %s: %s <br> Used %s: %s <br> Can be reduced by: %s " +
                        " <br> Kindly reach out to Nitish for any queries. If you aren't " +
                        "the service owner for the mail received, kindly help me out figuring the service owner",
                ownerEmail, entityToBeOptimized, entityToBeOptimized, Integer.toString(threshodMinUsagePercentage), app,
                entityToBeOptimized, Integer.toString(totalCPU), entityToBeOptimized, Integer.toString(usedCPU),
                Integer.toString(reduceBy)
        );
    }

    private String getExtendByMailBody(String app, int totalCPU, int usedCPU, int reduceBy, String ownerEmail,
                                                 int threshodMaxUsagePercentage, String entityToBeOptimized) {
        return String.format(
                "Hi, %s <br> App %s can be optimized. %s usage is consistently above %s%% in last 8 days. " +
                        " <br>App: %s  <br> Total %s: %s <br> Used %s: %s <br> Can be extended by: %s " +
                        " <br> Kindly reach out to Nitish for any queries. If you aren't " +
                        "the service owner for the mail received, kindly help me out figuring the service owner",
                ownerEmail, entityToBeOptimized, entityToBeOptimized, Integer.toString(threshodMaxUsagePercentage), app,
                entityToBeOptimized, Integer.toString(totalCPU), entityToBeOptimized, Integer.toString(usedCPU),
                Integer.toString(reduceBy)
        );
    }

    private String getAppName(String appId) {
        try {
            HttpGet request = new HttpGet(mesosMonitorConfig.getMesosEndpoint() + "/v2/apps/" + appId);
            HttpResponse response = client.execute(request);
            if(response == null) {
                return null;
            }
            int status = response.getStatusLine()
                    .getStatusCode();
            if(status < STATUS_OK_RANGE_START || status >= STATUS_OK_RANGE_END) {
                LOGGER.error("Error in Http get, Status Code: " + response.getStatusLine()
                        .getStatusCode() + " received Response: " + response);
                return null;
            }
            String data = EntityUtils.toString(response.getEntity());
            JSONObject jsonObject = new JSONObject(data);
            JSONObject appJsonObject = getObjectFromJSONObject(jsonObject, "app");
            JSONObject labelsJsonObject = getObjectFromJSONObject(appJsonObject, "labels");
            return getStringFromJSONObject(labelsJsonObject, "traefik.backend");
        } catch (Exception e) {
            LOGGER.error("Error in getting app name: " + e.getMessage(), e);
            return null;
        }
    }

}
