package com.optimizer.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@Data
@Builder
@AllArgsConstructor
public class AerospikeConfig {

    private String hosts;

    private String namespace;

    private int maxConnectionsPerNode;

    private int timeout;

    private int retries;

    private int sleepBetweenRetries;

    private int ttl;

}
