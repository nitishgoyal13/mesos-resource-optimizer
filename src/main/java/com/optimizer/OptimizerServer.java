package com.optimizer;

import com.optimizer.config.OptimizerConfig;
import com.optimizer.config.ServiceConfig;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.grafana.config.GrafannaConfig;
import com.optimizer.mail.MailSender;
import com.optimizer.threadpool.HystrixThreadPoolService;
import com.optimizer.threadpool.config.ThreadPoolConfig;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.util.List;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

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
            hystrixThreadPoolConfig = ThreadPoolConfig.builder()
                    .build();
        }
        //TODO Why do we need this. We should optimize all the pools. And whenever we don't have a mapping for the email, we can send the
        // email to the default email id
        //If we don't do this, we will have to keep updating our config for the new pools
        List<ServiceConfig> serviceConfigs = configuration.getServiceConfigs();
        MailSender mailSender = new MailSender(configuration.getMail());

        GrafannaConfig grafannaConfig = configuration.getGrafannaConfig();
        GrafanaService grafanaService = GrafanaService.builder()
                .grafannaConfig(grafannaConfig)
                .client(httpClient)
                .build();

        HystrixThreadPoolService hystrixThreadPoolService = new HystrixThreadPoolService(grafanaService, hystrixThreadPoolConfig,
                                                                                         mailSender, serviceConfigs
        );

        //TODO Instead of timer, use scheduledThreadPool executor. Timer thread dies in case of exception
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(hystrixThreadPoolService,
                                  hystrixThreadPoolConfig.getInitialDelayInSeconds() * TimeUnit.SECONDS.toMillis(1),
                                  hystrixThreadPoolConfig.getIntervalInSeconds() * TimeUnit.SECONDS.toMillis(1)
                                 );

        //TODO Also expose an API to trigger this from outside. It will help us debug in stage or prod

        environment.lifecycle()
                .manage(mailSender);
    }
}
