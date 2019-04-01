package com.optimizer.persistence;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.policy.*;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.optimizer.config.AerospikeConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@Data
@AllArgsConstructor
@Builder
@Slf4j
public class AerospikeClientManagaer {

    private static IAerospikeClient client;

    private static Policy readPolicy;

    private static WritePolicy writePolicy;

    private static AerospikeConfig config;


    private static void init(AerospikeConfig aerospikeConfig){
        config = aerospikeConfig;

        readPolicy = new Policy();
        readPolicy.consistencyLevel = ConsistencyLevel.CONSISTENCY_ONE;
        readPolicy.maxRetries = config.getRetries();
        readPolicy.replica = Replica.MASTER_PROLES;
        readPolicy.sleepBetweenRetries = config.getSleepBetweenRetries();
        readPolicy.sendKey = true;
        readPolicy.totalTimeout = config.getTimeout();

        writePolicy = new WritePolicy();
        writePolicy.maxRetries = config.getRetries();
        writePolicy.consistencyLevel = ConsistencyLevel.CONSISTENCY_ALL;
        writePolicy.replica = Replica.MASTER_PROLES;
        writePolicy.sleepBetweenRetries = config.getSleepBetweenRetries();
        writePolicy.commitLevel = CommitLevel.COMMIT_ALL;
        writePolicy.totalTimeout = config.getTimeout();
        writePolicy.sendKey = true;
        writePolicy.expiration = config.getTtl();


        val clientPolicy = new ClientPolicy();
        clientPolicy.maxConnsPerNode = config.getMaxConnectionsPerNode();
        clientPolicy.readPolicyDefault = readPolicy;
        clientPolicy.writePolicyDefault = writePolicy;
        clientPolicy.failIfNotConnected = true;
        clientPolicy.requestProleReplicas = true;
        clientPolicy.threadPool = Executors.newFixedThreadPool(64);
        clientPolicy.connPoolsPerNode = config.getMaxConnectionsPerNode();
        clientPolicy.sharedThreadPool = true;

        val hosts = config.getHosts().split(",");
        client = new AerospikeClient(clientPolicy, Arrays.stream(hosts).map(h -> {
            String[] host = h.split(":");
            if (host.length == 2) {
                return new Host(host[0], Integer.parseInt(host[1]));
            } else {
                return new Host(host[0], 3000);
            }
        }).toArray(Host[]::new));
        log.info("Aerospike connection status: " +client.isConnected());
    }

    public static void close() {
        if(null != client) {
            client.close();
        }
    }

    public static WritePolicy getWritePolicy(int ttl) throws ExecutionException {
        return writePolicyCache.get(ttl);
    }

    public static IAerospikeClient getClient() {
        Preconditions.checkNotNull(client);
        return client;
    }

    private static LoadingCache<Integer, WritePolicy> writePolicyCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<Integer, WritePolicy>() {
                @Override
                public WritePolicy load(Integer key) {
                    WritePolicy wp = new WritePolicy();
                    wp.maxRetries = config.getRetries();
                    wp.consistencyLevel = ConsistencyLevel.CONSISTENCY_ALL;
                    wp.replica = Replica.MASTER_PROLES;
                    wp.sleepBetweenRetries = config.getSleepBetweenRetries();
                    wp.commitLevel = CommitLevel.COMMIT_ALL;
                    wp.totalTimeout = config.getTimeout();
                    wp.sendKey = true;
                    wp.expiration = key;
                    return wp;
                }
            });

}
