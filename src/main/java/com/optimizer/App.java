package com.optimizer;

import com.optimizer.config.OptimizerConfig;
import io.dropwizard.Application;
import io.dropwizard.riemann.RiemannBundle;
import io.dropwizard.riemann.RiemannConfig;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

/***
 Created by nitish.goyal on 18/02/19
 ***/
public class App extends Application<OptimizerConfig> {

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

    }
}
