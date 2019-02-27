package com.optimizer.grafana;

/***
 Created by nitish.goyal on 27/02/19
 ***/
public class GrafanaQueryUtils {

    public static final String SERVICE_LIST_QUERY = "SHOW MEASUREMENTS with measurement = /phonepe.prod.*.jvm.threads.count/";
    public static final String SERVICE_LIST_PATTERN = "phonepe.prod.(.*).jvm.threads.count";

}
