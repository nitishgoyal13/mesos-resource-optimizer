package com.optimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/***
 Created by nitish.goyal on 28/03/19
 ***/
@Data
@AllArgsConstructor
@Builder
public class OptimisedConfig {

    private String cluster;
    private String host;
    private String service;
    private String pool;

    private int optimisedPoolValue;
}
