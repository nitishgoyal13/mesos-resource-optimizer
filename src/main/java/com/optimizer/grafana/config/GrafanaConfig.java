package com.optimizer.grafana.config;

import lombok.Data;

import java.util.List;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@Data
public class GrafanaConfig {

    private List<HeaderConfig> headers;

    private String url;

    private String prefix;
}
