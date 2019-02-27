package com.optimizer.config;

import lombok.Data;

/***
 Created by mudit.g on Feb, 2019
 ***/
@Data
public class ThreadPoolConfig {
    private int thresholdUsagePercentage = 60;

    private int maxUsagePercentage = 80;

    private int queryDuration = 72;
}
