package com.optimizer.config;

import lombok.Data;

import javax.validation.constraints.NotNull;

/***
 Created by mudit.g on Mar, 2019
 ***/
@Data
public class AppConfig {

    @NotNull
    private String app;

    @NotNull
    private String ownerEmail;
}
