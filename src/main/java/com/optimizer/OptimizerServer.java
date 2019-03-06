package com.optimizer;

import com.optimizer.config.OptimizerConfig;
import com.optimizer.config.ServiceConfig;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.grafana.config.GrafannaConfig;
import com.optimizer.mail.MailSender;
import com.optimizer.resources.ThreadPoolResource;
import com.optimizer.threadpool.HystrixThreadPoolService;
import com.optimizer.threadpool.config.ThreadPoolConfig;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

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
        HttpClient httpClient = HttpClientBuilder.create()
                .build();
        ThreadPoolConfig hystrixThreadPoolConfig = configuration.getThreadPoolConfig();
        if(hystrixThreadPoolConfig == null) {
            hystrixThreadPoolConfig = ThreadPoolConfig.builder()
                    .build();
        }
        List<ServiceConfig> serviceConfigs = configuration.getServiceConfigs();
        MailSender mailSender = new MailSender(configuration.getMail());

        GrafannaConfig grafannaConfig = configuration.getGrafannaConfig();
        GrafanaService grafanaService = GrafanaService.builder()
                .grafannaConfig(grafannaConfig)
                .client(httpClient)
                .build();
        Map<String, String> serviceVsOwnerMap = createServiceVsOwnerMap(serviceConfigs);
        HystrixThreadPoolService hystrixThreadPoolService = HystrixThreadPoolService.builder()
                .grafanaService(grafanaService)
                .threadPoolConfig(hystrixThreadPoolConfig)
                .mailSender(mailSender)
                .serviceVsOwnerMap(serviceVsOwnerMap)
                .build();

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(hystrixThreadPoolService, hystrixThreadPoolConfig.getInitialDelayInSeconds(),
                                                     hystrixThreadPoolConfig.getIntervalInSeconds(), TimeUnit.SECONDS
                                                    );
        //TODO Delete later
        hystrixThreadPoolService.run();

        environment.lifecycle()
                .manage(mailSender);

        environment.jersey()
                .register(new ThreadPoolResource(hystrixThreadPoolService));
    }

    private Map<String, String> createServiceVsOwnerMap(List<ServiceConfig> serviceConfigs) {
        Map<String, String> serviceVsOwnerMap = new HashMap<>();
        serviceConfigs.forEach(serviceConfig -> serviceVsOwnerMap.put(serviceConfig.getService(), serviceConfig.getOwnerEmail()));
        return serviceVsOwnerMap;
    }
}