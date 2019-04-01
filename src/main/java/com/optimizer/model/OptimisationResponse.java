package com.optimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/***
 Created by nitish.goyal on 28/03/19
 ***/
@Builder
@AllArgsConstructor
@Data
public class OptimisationResponse {

    private List<OptimisedConfig> configs;
}
