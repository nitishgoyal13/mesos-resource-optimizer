package com.optimizer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/***
 Created by nitish.goyal on 28/03/19
 ***/
@AllArgsConstructor
@Builder
@Data
public class HostConfig {

    private Map<String, Integer> poolVsValue;
}
