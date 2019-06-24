package com.optimizer.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 24/06/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThresholdParams {

    private boolean enabled;

    private int minResourcePercentage = 50;
    private int maxResourcePercentage = 70;
    private int reduceThreshold = 50;
    private int extendThreshold = 50;
}