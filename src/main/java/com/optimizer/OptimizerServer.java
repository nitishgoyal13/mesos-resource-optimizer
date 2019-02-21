package com.optimizer;

import com.optimizer.config.OptimizerConfig;
import com.optimizer.threadpool.HystrixThreadPoolService;
import com.optimizer.threadpool.Service;
import io.dropwizard.Application;
import io.dropwizard.riemann.RiemannBundle;
import io.dropwizard.riemann.RiemannConfig;
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

        bootstrap.addBundle(new RiemannBundle<OptimizerConfig>() {
            @Override
            public RiemannConfig getRiemannConfiguration(OptimizerConfig configuration) {
                return configuration.getRiemann();
            }
        });

    }

    @Override
    public void run(OptimizerConfig configuration, Environment environment) throws Exception {
        HttpClient httpClient = HttpClientBuilder.create().build();
        Service service = new Service(httpClient);
        HystrixThreadPoolService hystrixThreadPoolService = new HystrixThreadPoolService(httpClient, service);
        hystrixThreadPoolService.handleHystrixPools();
    }
}
