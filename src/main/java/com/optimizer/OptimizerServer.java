package com.optimizer;

import com.optimizer.config.GrafannaConfig;
import com.optimizer.config.OptimizerConfig;
import com.optimizer.config.ServiceConfig;
import com.optimizer.config.ThreadPoolConfig;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.mail.MailSender;
import com.optimizer.threadpool.HystrixThreadPoolService;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.util.List;
import java.util.Timer;

/***
 Created by nitish.goyal on 18/02/19
 ***/
public class OptimizerServer extends Application<OptimizerConfig> {

    @Override
    public void initialize(Bootstrap<OptimizerConfig> bootstrap) {

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
            hystrixThreadPoolConfig = new ThreadPoolConfig();
        }
        List<ServiceConfig> serviceConfigs = configuration.getServiceConfigs();
        MailSender mailSender = new MailSender(configuration.getMail());

        GrafannaConfig grafannaConfig = configuration.getGrafannaConfig();
        GrafanaService grafanaService = new GrafanaService(httpClient, grafannaConfig);

        HystrixThreadPoolService hystrixThreadPoolService = new HystrixThreadPoolService(grafanaService,
                                                                                         hystrixThreadPoolConfig,
                                                                                         mailSender, serviceConfigs
        );

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(hystrixThreadPoolService,
                hystrixThreadPoolConfig.getInitialDelayInSeconds() * 1000,
                hystrixThreadPoolConfig.getIntervalInSeconds() * 1000);
    }
}
