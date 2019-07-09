package com.optimizer.config;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@Data
@Builder
public class ServiceConfig {

    @NotNull
    private String service;

    @NotNull
    private String ownerEmail;
}
