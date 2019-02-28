package com.optimizer.config;

import io.dropwizard.Configuration;
import io.dropwizard.riemann.RiemannConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.Valid;
import java.util.List;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class OptimizerConfig extends Configuration {

    @Valid
    private RiemannConfig riemann;

    private List<ServiceConfig> serviceConfigs;

    private GrafannaConfig grafannaConfig;

    private ThreadPoolConfig hystrixThreadPoolConfig;
}
