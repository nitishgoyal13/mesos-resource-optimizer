package com.optimizer.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.optimizer.util.OptimizerUtils;
import javax.ws.rs.DefaultValue;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/***
 Created by mudit.g on Mar, 2019
 ***/
@Getter
@Setter
@Builder
public class MesosMonitorConfig {

    private boolean enabled = true;

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
