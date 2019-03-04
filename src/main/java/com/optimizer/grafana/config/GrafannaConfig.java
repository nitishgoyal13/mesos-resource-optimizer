package com.optimizer.grafana.config;

import lombok.Data;

import java.util.List;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@Data
public class GrafannaConfig {

    private List<HeaderConfig> headers;
}
