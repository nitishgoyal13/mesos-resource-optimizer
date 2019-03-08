package com.optimizer.threadpool.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.ws.rs.DefaultValue;

/***
 Created by mudit.g on Feb, 2019
 ***/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ThreadPoolConfig {

    private int thresholdUsagePercentage = 60;

    private int queryDurationInHours = 72;

    private int initialDelayInSeconds = 1;

    private int intervalInSeconds = 86400;

    @DefaultValue("50")
    private int reduceByThreshold = 50;

    @DefaultValue("50")
    private int extendByThreshold = 50;

}
