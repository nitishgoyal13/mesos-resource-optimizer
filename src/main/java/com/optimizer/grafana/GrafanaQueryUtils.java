package com.optimizer.grafana;

/***
 Created by nitish.goyal on 27/02/19
 ***/
@SuppressWarnings("WeakerAccess")
public class GrafanaQueryUtils {

    public static final String POOL_LIST_QUERY = "SHOW MEASUREMENTS with measurement = /%s.*.propertyValue_corePoolSize/";

    public static final String POOL_LIST_PATTERN = "%s.(.*).propertyValue_corePoolSize";
}
