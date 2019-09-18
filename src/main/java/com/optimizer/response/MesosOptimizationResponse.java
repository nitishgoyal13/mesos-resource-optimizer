package com.optimizer.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 24/06/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MesosOptimizationResponse {

    private List<AppOptimizationResponse> appsOptimizedList;

}
