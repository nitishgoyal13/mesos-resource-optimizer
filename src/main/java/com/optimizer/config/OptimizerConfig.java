package com.optimizer.config;

import com.optimizer.mail.config.MailConfig;
import io.dropwizard.Configuration;
import io.dropwizard.riemann.RiemannConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public class OptimizerConfig extends Configuration {

    private RiemannConfig riemann;

    private List<ServiceConfig> serviceConfigs;

    private GrafannaConfig grafannaConfig;

    private ThreadPoolConfig threadPoolConfig;

    private MailConfig mail;

    public RiemannConfig getRiemann() {
        return riemann;
    }

    public List<ServiceConfig> getServiceConfigs() {
        return serviceConfigs;
    }

    public GrafannaConfig getGrafannaConfig() {
        return grafannaConfig;
    }

    public ThreadPoolConfig getThreadPoolConfig() {
        return threadPoolConfig;
    }
}
