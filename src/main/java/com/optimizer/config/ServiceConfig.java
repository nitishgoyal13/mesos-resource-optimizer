package com.optimizer.config;

import lombok.Data;

import javax.validation.constraints.NotNull;

/***
 Created by nitish.goyal on 19/02/19
 ***/
@Data
public class ServiceConfig {

    @NotNull
    private String service;

    @NotNull
    private String ownerEmail;
}
