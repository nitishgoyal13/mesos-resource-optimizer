package com.optimizer.resources;

import com.optimizer.threadpool.HystrixThreadPoolRunnable;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/***
 Created by mudit.g on Mar, 2019
 ***/
@Path("/optimizer")
public class OptimizerResource {

    private HystrixThreadPoolRunnable hystrixThreadPoolRunnable;

    public OptimizerResource(HystrixThreadPoolRunnable hystrixThreadPoolRunnable) {
        this.hystrixThreadPoolRunnable = hystrixThreadPoolRunnable;
    }

    @GET
    @Path("/pool")
    public void poolOptimizer() {
        hystrixThreadPoolRunnable.run();
    }
}
