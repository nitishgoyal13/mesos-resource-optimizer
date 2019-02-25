package com.optimizer.config;

import io.dropwizard.Configuration;
import io.dropwizard.riemann.RiemannConfig;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.Valid;
import java.util.List;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@Data
@AllArgsConstructor
public class OptimizerConfig extends Configuration {

    @Valid
    private RiemannConfig riemann;

    private List<ServiceConfig> serviceConfigs;

    private GrafannaConfig grafannaConfig;

}
