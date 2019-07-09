package com.optimizer;

import com.optimizer.config.GrafanaConfig;
import com.optimizer.config.MailConfig;
import com.optimizer.config.MesosMonitorConfig;
import com.optimizer.config.OptimizerConfig;
import com.optimizer.config.ServiceConfig;
import com.optimizer.config.ThreadPoolConfig;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.http.HttpClientFactory;
import com.optimizer.mail.MailSender;
import com.optimizer.mesosmonitor.MesosMonitorRunnable;
import com.optimizer.resources.OptimizerResource;
import com.optimizer.threadpool.HystrixThreadPoolHostRunnable;
import com.optimizer.threadpool.HystrixThreadPoolRunnable;
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

/***
 Created by nitish.goyal on 18/02/19
 ***/
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
                    new SubstitutingSourceProvider(new RoseyConfigSourceProvider("platform", "optimizer"),
                            new EnvironmentVariableSubstitutor()));
        }
    }

    @Override
    public void run(OptimizerConfig configuration, Environment environment) {

        ThreadPoolConfig hystrixThreadPoolConfig = configuration.getThreadPoolConfig();
        if (hystrixThreadPoolConfig == null) {
            hystrixThreadPoolConfig = ThreadPoolConfig.builder()
                    .build();
            configuration.setThreadPoolConfig(hystrixThreadPoolConfig);
        }
        MesosMonitorConfig mesosMonitorConfig = configuration.getMesosMonitorConfig();
        if (mesosMonitorConfig == null) {
            mesosMonitorConfig = MesosMonitorConfig.builder()
                    .build();
            configuration.setMesosMonitorConfig(mesosMonitorConfig);
        }
        GrafanaConfig grafanaConfig = configuration.getGrafanaConfig();
        MailConfig mailConfig = configuration.getMailConfig();

        List<ServiceConfig> serviceConfigs = configuration.getServiceConfigs();
        MailSender mailSender = new MailSender(mailConfig);

        GrafanaService grafanaService = GrafanaService.builder()
                .grafanaConfig(grafanaConfig)
                .client(HttpClientFactory.getHttpClient())
                .build();
        Map<String, String> serviceVsOwnerMap = createServiceVsOwnerMap(serviceConfigs);

        Map<String, String> serviceVsOwnerEmail = createAppVsOwnerMap(serviceConfigs);

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);

        HystrixThreadPoolRunnable hystrixThreadPoolRunnable = HystrixThreadPoolRunnable.builder()
                .grafanaService(grafanaService)
                .threadPoolConfig(hystrixThreadPoolConfig)
                .mailSender(mailSender)
                .serviceVsOwnerMap(serviceVsOwnerMap)
                .mailConfig(mailConfig)
                .grafanaConfig(grafanaConfig)
                .clusters(configuration.getClusters())
                .build();

        optimizeThreadPools(grafanaService, mailSender, serviceVsOwnerEmail, configuration, scheduledExecutorService);

        optimizeMesosResources(configuration, mailSender, grafanaService, serviceVsOwnerEmail,
                scheduledExecutorService);

        environment.lifecycle()
                .manage(mailSender);

        if (configuration.getThreadPoolConfig()
                .isEnabled()) {
            scheduledExecutorService
                    .scheduleAtFixedRate(hystrixThreadPoolRunnable, hystrixThreadPoolConfig.getInitialDelayInSeconds(),
                            hystrixThreadPoolConfig.getIntervalInSeconds(), TimeUnit.SECONDS
                    );
        }

        environment.jersey()
                .register(new OptimizerResource(hystrixThreadPoolRunnable));
    }

    private void optimizeMesosResources(OptimizerConfig optimizerConfig, MailSender mailSender,
            GrafanaService grafanaService,
            Map<String, String> serviceVsOwnerEmail, ScheduledExecutorService scheduledExecutorService) {
        MesosMonitorConfig mesosMonitorConfig = optimizerConfig.getMesosMonitorConfig();
        if (!mesosMonitorConfig.isEnabled()) {
            return;
        }
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

    private void optimizeThreadPools(GrafanaService grafanaService, MailSender mailSender,
            Map<String, String> serviceVsOwnerEmail,
            OptimizerConfig configuration, ScheduledExecutorService scheduledExecutorService) {

        if (!configuration.getThreadPoolConfig()
                .isEnabled()) {
            return;
        }
        ThreadPoolConfig threadPoolConfig = configuration.getThreadPoolConfig();
        HystrixThreadPoolHostRunnable hystrixThreadPoolHostRunnable = HystrixThreadPoolHostRunnable.builder()
                .grafanaService(grafanaService)
                .threadPoolConfig(threadPoolConfig)
                .mailSender(mailSender)
                .serviceVsOwnerMap(serviceVsOwnerEmail)
                .mailConfig(configuration.getMailConfig())
                .grafanaConfig(configuration.getGrafanaConfig())
                .clusters(configuration.getClusters())
                .build();

        scheduledExecutorService
                .scheduleAtFixedRate(hystrixThreadPoolHostRunnable, threadPoolConfig.getInitialDelayInSeconds(),
                        threadPoolConfig.getIntervalInSeconds(), TimeUnit.SECONDS
                );


    }

    private Map<String, String> createServiceVsOwnerMap(List<ServiceConfig> serviceConfigs) {
        Map<String, String> serviceVsOwnerMap = new HashMap<>();
        serviceConfigs.forEach(
                serviceConfig -> serviceVsOwnerMap.put(serviceConfig.getService(), serviceConfig.getOwnerEmail()));
        return serviceVsOwnerMap;
    }

    private Map<String, String> createAppVsOwnerMap(List<ServiceConfig> serviceConfigs) {
        Map<String, String> appVsOwnerMap = new HashMap<>();
        serviceConfigs
                .forEach(serviceConfig -> appVsOwnerMap.put(serviceConfig.getService(), serviceConfig.getOwnerEmail()));
        return appVsOwnerMap;
    }

}
