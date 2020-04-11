package com.optimizer;

import com.optimizer.config.MailConfig;
import com.optimizer.config.MesosMonitorConfig;
import com.optimizer.config.OptimizerConfig;
import com.optimizer.config.ServiceConfig;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.http.HttpClientFactory;
import com.optimizer.mail.MailSender;
import com.optimizer.mesosmonitor.MesosMonitorRunnable;
import com.phonepe.rosey.dwconfig.RoseyConfigSourceProvider;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/***
 Created by nitish.goyal on 18/02/19
 ***/
@Slf4j
public class OptimizerServer extends Application<OptimizerConfig> {

    @Override
    public void initialize(Bootstrap<OptimizerConfig> bootstrap) {
        boolean localConfig = Boolean.parseBoolean(System.getProperty("localConfig", "false"));
        if (localConfig) {
            bootstrap.setConfigurationSourceProvider(
                    new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                            new EnvironmentVariableSubstitutor()));
        } else {
            bootstrap.setConfigurationSourceProvider(
                    new SubstitutingSourceProvider(new RoseyConfigSourceProvider("dataplatform", "optimizer"),
                            new EnvironmentVariableSubstitutor()));
        }
    }

    @Override
    public void run(OptimizerConfig configuration, Environment environment) {

        MesosMonitorConfig mesosMonitorConfig = configuration.getMesosMonitorConfig();
        if (mesosMonitorConfig == null) {
            mesosMonitorConfig = MesosMonitorConfig.builder()
                    .build();
            configuration.setMesosMonitorConfig(mesosMonitorConfig);
        }
        log.info("Mesos Montior config : " + mesosMonitorConfig);
        MailConfig mailConfig = configuration.getMailConfig();

        MailSender mailSender = new MailSender(mailConfig);

        optimizeMesosResources(configuration, mailSender);

        environment.lifecycle()
                .manage(mailSender);
    }

    private void optimizeMesosResources(OptimizerConfig optimizerConfig, MailSender mailSender) {

        List<ServiceConfig> serviceConfigs = optimizerConfig.getServiceConfigs();
        Map<String, String> serviceVsOwnerEmail = createAppVsOwnerMap(serviceConfigs);
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);

        MesosMonitorConfig mesosMonitorConfig = optimizerConfig.getMesosMonitorConfig();
        if (!mesosMonitorConfig.isEnabled()) {
            return;
        }

        GrafanaService grafanaService = GrafanaService.builder()
                .grafanaConfig(optimizerConfig.getGrafanaConfig())
                .client(HttpClientFactory.getHttpClient())
                .build();

        MesosMonitorRunnable mesosMonitorRunnable = MesosMonitorRunnable.builder()
                .grafanaService(grafanaService)
                .mesosMonitorConfig(mesosMonitorConfig)
                .appVsOwnerMap(serviceVsOwnerEmail)
                .mailConfig(optimizerConfig.getMailConfig())
                .mailSender(mailSender)
                .grafanaConfig(optimizerConfig.getGrafanaConfig())
                .build();

        scheduledExecutorService
                .scheduleAtFixedRate(mesosMonitorRunnable, mesosMonitorConfig.getInitialDelayInSeconds(),
                        mesosMonitorConfig.getIntervalInSeconds(), TimeUnit.SECONDS
                );
    }

    private Map<String, String> createAppVsOwnerMap(List<ServiceConfig> serviceConfigs) {
        Map<String, String> appVsOwnerMap = new HashMap<>();
        serviceConfigs
                .forEach(serviceConfig -> appVsOwnerMap.put(serviceConfig.getService(), serviceConfig.getOwnerEmail()));
        return appVsOwnerMap;
    }

}
