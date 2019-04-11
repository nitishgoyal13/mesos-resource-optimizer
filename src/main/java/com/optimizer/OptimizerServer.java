package com.optimizer;

import com.optimizer.config.AppConfig;
import com.optimizer.config.OptimizerConfig;
import com.optimizer.config.ServiceConfig;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.grafana.config.GrafanaConfig;
import com.optimizer.http.HttpClientFactory;
import com.optimizer.mail.MailSender;
import com.optimizer.mail.config.MailConfig;
import com.optimizer.mesosmonitor.MesosMonitorService;
import com.optimizer.mesosmonitor.config.MesosMonitorConfig;
import com.optimizer.resources.ThreadPoolResource;
import com.optimizer.threadpool.HystrixThreadPoolHostService;
import com.optimizer.threadpool.HystrixThreadPoolService;
import com.optimizer.threadpool.config.ThreadPoolConfig;
import io.dropwizard.Application;
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

        //TODO Integration with configservice and deploy it in stage
        /*bootstrap.addBundle(new RiemannBundle<OptimizerConfig>() {
            @Override
            public RiemannConfig getRiemannConfiguration(OptimizerConfig configuration) {
                return configuration.getRiemann();
            }
        });*/

    }

    @Override
    public void run(OptimizerConfig configuration, Environment environment) throws Exception {

        ThreadPoolConfig hystrixThreadPoolConfig = configuration.getThreadPoolConfig();
        if(hystrixThreadPoolConfig == null) {
            hystrixThreadPoolConfig = ThreadPoolConfig.builder()
                    .build();
        }
        MesosMonitorConfig mesosMonitorConfig = configuration.getMesosMonitorConfig();
        if(mesosMonitorConfig == null) {
            mesosMonitorConfig = MesosMonitorConfig.builder()
                    .build();
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

        List<AppConfig> appConfigs = configuration.getAppConfigs();
        Map<String, String> appVsOwnerMap = createAppVsOwnerMap(appConfigs);

        HystrixThreadPoolService hystrixThreadPoolService = HystrixThreadPoolService.builder()
                .grafanaService(grafanaService)
                .threadPoolConfig(hystrixThreadPoolConfig)
                .mailSender(mailSender)
                .serviceVsOwnerMap(serviceVsOwnerMap)
                .mailConfig(mailConfig)
                .grafanaConfig(grafanaConfig)
                .clusters(configuration.getClusters())
                .build();

        HystrixThreadPoolHostService hystrixThreadPoolHostService = HystrixThreadPoolHostService.builder()
                .grafanaService(grafanaService)
                .threadPoolConfig(hystrixThreadPoolConfig)
                .mailSender(mailSender)
                .serviceVsOwnerMap(serviceVsOwnerMap)
                .mailConfig(mailConfig)
                .grafanaConfig(grafanaConfig)
                .clusters(configuration.getClusters())
                .build();

        MesosMonitorService mesosMonitorService = MesosMonitorService.builder()
                .grafanaService(grafanaService)
                .mesosMonitorConfig(mesosMonitorConfig)
                .appVsOwnerMap(appVsOwnerMap)
                .mailConfig(mailConfig)
                .mailSender(mailSender)
                .grafanaConfig(grafanaConfig)
                .build();

        environment.lifecycle()
                .manage(mailSender);

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
        /*scheduledExecutorService.scheduleAtFixedRate(hystrixThreadPoolService, hystrixThreadPoolConfig.getInitialDelayInSeconds(),
                                                     hystrixThreadPoolConfig.getIntervalInSeconds(), TimeUnit.SECONDS
                                                    );
        scheduledExecutorService.scheduleAtFixedRate(hystrixThreadPoolHostService, hystrixThreadPoolConfig.getInitialDelayInSeconds(),
                                                     hystrixThreadPoolConfig.getIntervalInSeconds(), TimeUnit.SECONDS
                                                    );*/
        scheduledExecutorService.scheduleAtFixedRate(mesosMonitorService, mesosMonitorConfig.getInitialDelayInSeconds(),
                                                     mesosMonitorConfig.getIntervalInSeconds(), TimeUnit.SECONDS
                                                    );

        environment.jersey()
                .register(new ThreadPoolResource(hystrixThreadPoolService));
    }

    private Map<String, String> createServiceVsOwnerMap(List<ServiceConfig> serviceConfigs) {
        Map<String, String> serviceVsOwnerMap = new HashMap<>();
        serviceConfigs.forEach(serviceConfig -> serviceVsOwnerMap.put(serviceConfig.getService(), serviceConfig.getOwnerEmail()));
        return serviceVsOwnerMap;
    }

    private Map<String, String> createAppVsOwnerMap(List<AppConfig> appConfigs) {
        Map<String, String> appVsOwnerMap = new HashMap<>();
        appConfigs.forEach(serviceConfig -> appVsOwnerMap.put(serviceConfig.getApp(), serviceConfig.getOwnerEmail()));
        return appVsOwnerMap;
    }
}
