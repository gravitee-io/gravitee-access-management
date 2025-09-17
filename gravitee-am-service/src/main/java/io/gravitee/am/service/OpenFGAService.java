/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import io.gravitee.am.service.model.OpenFGAAuthenticationModel;
import io.gravitee.am.service.model.OpenFGAConnectResponse;
import io.gravitee.am.service.model.OpenFGAStoreEntity;
import io.gravitee.am.service.model.OpenFGATuple;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.util.concurrent.ExecutionException;

/**
 * @author GraviteeSource Team
 */
public interface OpenFGAService {

    /**
     * Test connection to an OpenFGA server
     * @param serverUrl The OpenFGA server URL
     * @return Single indicating connection success
     */
    Single<OpenFGAConnectResponse> connect(String serverUrl);

    Flowable<OpenFGAStoreEntity> getStores();

    Single<OpenFGAStoreEntity> createStore(String storeName) throws FgaInvalidParameterException;

    Single<OpenFGAAuthenticationModel> setAuthenticationModel(String storeId, String model) throws JsonProcessingException, FgaInvalidParameterException;

    Flowable<OpenFGATuple> getTuples(String storeId) throws FgaInvalidParameterException, ExecutionException, InterruptedException;

    Single<OpenFGATuple> createTuple(String storeId, OpenFGATuple tuple) throws FgaInvalidParameterException;

//    Single<OpenFGACheckResponse> check(OpenFGATuple tuple);

}