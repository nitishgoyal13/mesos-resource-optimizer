package com.optimizer;

import com.optimizer.config.OptimizerConfig;
import com.optimizer.config.ThreadPoolConfig;
import com.optimizer.grafana.GrafanaService;
import com.optimizer.threadpool.HystrixThreadPoolService;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

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
        ThreadPoolConfig hystrixThreadPoolConfig = configuration.getHystrixThreadPoolConfig();
        if(hystrixThreadPoolConfig == null) {
            hystrixThreadPoolConfig = new ThreadPoolConfig();
        }
        GrafanaService grafanaService = GrafanaService.builder()
                .client(httpClient)
                .build();
        //TODO Comment : Always try to use builder pattern over new
        HystrixThreadPoolService hystrixThreadPoolService = HystrixThreadPoolService.builder()
                .client(httpClient)
                .grafanaService(grafanaService)
                .threadPoolConfig(hystrixThreadPoolConfig)
                .build();

        //TODO This should happen through a job
        hystrixThreadPoolService.handleHystrixPools();
    }
}
