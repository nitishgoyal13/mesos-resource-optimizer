package com.optimizer.config;

import io.dropwizard.Configuration;
import io.dropwizard.riemann.RiemannConfig;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@Builder
public class OptimizerConfig extends Configuration {

    private RiemannConfig riemann;

    private List<ServiceConfig> serviceConfigs;

    private GrafanaConfig grafanaConfig;

    private ThreadPoolConfig threadPoolConfig;

    private MailConfig mailConfig;

    private MesosMonitorConfig mesosMonitorConfig;

    private List<String> clusters;

}
