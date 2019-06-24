package com.optimizer.config;

import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

/***
 Created by mudit.g on Mar, 2019
 ***/
@Data
@Builder
public class MailConfig {
    @NotNull
    @NotEmpty
    private String host;

    @NotNull
    @NotEmpty
    private String user;

    @NotNull
    @NotEmpty
    private String password;


    private int port;

    @NotNull
    @NotEmpty
    private String from;

    private boolean defaultOwnersEnabled;

    private String defaultOwnersEmails;

    private boolean enabledForServiceOwners;
}
