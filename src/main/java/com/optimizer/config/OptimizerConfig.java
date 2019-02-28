package com.optimizer.config;

import io.dropwizard.Configuration;
import io.dropwizard.riemann.RiemannConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@Getter
public class OptimizerConfig extends Configuration {

    private final RiemannConfig riemann;

    private final List<ServiceConfig> serviceConfigs;

    private final GrafannaConfig grafannaConfig;

    private final ThreadPoolConfig hystrixThreadPoolConfig;
}
