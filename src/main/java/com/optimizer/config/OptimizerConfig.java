package com.optimizer.config;

import io.dropwizard.Configuration;
import io.dropwizard.riemann.RiemannConfig;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@Data
public class OptimizerConfig extends Configuration {

    @NotNull
    @Valid
    private RiemannConfig riemann;

    private List<ServiceConfig> serviceConfigs;

    private GrafannaConfig grafannaConfig;

}
