package com.optimizer.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.optimizer.util.OptimizerUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import javax.ws.rs.DefaultValue;

/***
 Created by mudit.g on Mar, 2019
 ***/
@Getter
@Setter
@Builder
public class MesosMonitorConfig {

    private boolean enabled;

    private String mesosEndpoint;

    @DefaultValue("MAX")
    private OptimizerUtils.ExtractionStrategy extractionStrategy = OptimizerUtils.ExtractionStrategy.MAX;

    @JsonProperty("cpuThresholds")
    private ThresholdParams cpuThresholds;

    @JsonProperty("memoryThresholds")
    private ThresholdParams memoryThresholds;

    private int initialDelayInSeconds = 1;

    private int intervalInSeconds = 86400;

    private int queryDurationInHours = 72;


}
