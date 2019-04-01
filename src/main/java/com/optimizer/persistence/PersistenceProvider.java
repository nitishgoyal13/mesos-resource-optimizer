package com.optimizer.persistence;

import com.optimizer.model.OptimisationResponse;

/***
 Created by nitish.goyal on 29/03/19
 ***/
public interface PersistenceProvider {

    boolean saveResponse(OptimisationResponse optimisationResponse);
}
