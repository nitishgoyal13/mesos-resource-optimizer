package com.optimizer.mesosmonitor.config;

import lombok.Builder;
import lombok.Data;

import javax.ws.rs.DefaultValue;

/***
 Created by mudit.g on Mar, 2019
 ***/
@Data
@Builder
public class MesosMonitorConfig {

    private String prefix;

    private String mesosEndpoint;

    private int thresholdMinCpuUsagePercentage = 50;

    private int thresholdMaxCpuUsagePercentage = 70;

    private int thresholdMinMemoryUsagePercentage = 50;

    private int thresholdMaxMemoryUsagePercentage = 70;

    @DefaultValue("50")
    private int cpuReduceByThreshold = 50;

    @DefaultValue("50")
    private int memoryReduceByThreshold = 50;

    @DefaultValue("50")
    private int cpuExtendByThreshold = 50;

    @DefaultValue("50")
    private int memoryExtendByThreshold = 50;

    private int initialDelayInSeconds = 1;

    private int intervalInSeconds = 86400;

    private int queryDurationInHours = 72;

}
