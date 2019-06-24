package com.optimizer.mesosmonitor;

/***
 Created by mudit.g on Mar, 2019
 ***/
public class MesosMonitorQueryUtils {

    public static final String TOTAL_CPU = "totalCpu";
    public static final String USED_CPU = "usedCpu";
    public static final String TOTAL_MEMORY = "totalMemory";
    public static final String USED_MEMORY = "usedMemory";

    public static final String APP_QUERY =
            "SELECT sum(\"value\") FROM \"%s.mesosmonitor.%s.metric.%s\" WHERE time > now() - %sh " +
                    "group by time(30s) fill(null)";
}
