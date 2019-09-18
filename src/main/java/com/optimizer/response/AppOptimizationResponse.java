package com.optimizer.response;

import com.optimizer.mesosmonitor.MesosMonitorRunnable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 24/06/19
 ***/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppOptimizationResponse {

    private String app;

    private long allocated;
    private long used;

    private long extendBy;
    private long reduceBy;

    private MesosMonitorRunnable.ResourcesOptimized resourcesOptimized;

}
