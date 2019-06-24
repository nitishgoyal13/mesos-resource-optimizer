package com.optimizer.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/***
 Created by nitish.goyal on 24/06/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MesosOptimizationResponse {

    private List<AppOptimizationResource> appsOptimizedList;

}
