package com.optimizer.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 24/06/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeaderConfig {

    @JsonProperty("name")
    private String name;

    @JsonProperty("value")
    private String value;
}
