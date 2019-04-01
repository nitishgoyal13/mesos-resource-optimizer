package com.optimizer.config;

import com.optimizer.grafana.config.GrafanaConfig;
import com.optimizer.mail.config.MailConfig;
import com.optimizer.mesosmonitor.config.MesosMonitorConfig;
import com.optimizer.threadpool.config.ThreadPoolConfig;
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

    private GrafanaConfig grafanaConfig;

    private ThreadPoolConfig threadPoolConfig;

    private MailConfig mailConfig;

    private List<AppConfig> appConfigs;

    private MesosMonitorConfig mesosMonitorConfig;

    private List<String> clusters;

    private AerospikeConfig aerospikeConfig;

}
