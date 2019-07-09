package com.optimizer.config;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GrafanaConfig {

    private List<HeaderConfig> headers;

    private String url;

    private String prefix;

}

