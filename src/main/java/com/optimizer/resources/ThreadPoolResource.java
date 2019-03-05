package com.optimizer.resources;

import com.optimizer.threadpool.HystrixThreadPoolService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/***
 Created by mudit.g on Mar, 2019
 ***/
@Path("/optimizer/pool")
public class ThreadPoolResource {

    private HystrixThreadPoolService hystrixThreadPoolService;

    public ThreadPoolResource(HystrixThreadPoolService hystrixThreadPoolService) {
        this.hystrixThreadPoolService = hystrixThreadPoolService;
    }

    @GET
    public void poolOptimizer() {
        hystrixThreadPoolService.run();
    }
}
