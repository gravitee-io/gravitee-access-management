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
package io.gravitee.am.service.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientCreateStoreOptions;
import dev.openfga.sdk.api.configuration.ClientListStoresOptions;
import dev.openfga.sdk.api.configuration.ClientWriteAuthorizationModelOptions;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.CreateStoreResponse;
import dev.openfga.sdk.api.model.ListStoresResponse;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.api.model.WriteAuthorizationModelResponse;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

/**
 * Spring Service for OpenFGA operations:
 *  - connecting to OpenFGA using only API URL,
 *  - listing stores,
 *  - creating a store.
 *
 * Requires: dev.openfga:openfga-sdk
 */
@Service
public class OpenFgaService {

    private OpenFgaClient fga;

    /**
     * List all stores (paginated). Pass nulls for defaults.
     */


    /**
     * Create a new store with the given name.
     * Returns CreateStoreResponse (contains the new store ID).
     */
    public CreateStoreResponse createStore(String name) throws ExecutionException, InterruptedException {
        CreateStoreRequest req = new CreateStoreRequest().name(name);
        ClientCreateStoreOptions opts = new ClientCreateStoreOptions();
        CreateStoreResponse store;
        try {
            store = fga.createStore(req, opts).get();
        } catch (FgaInvalidParameterException e) {
            throw new RuntimeException(e);
        }

        // Optional: set this storeId on the client if you plan more operations on it
        // fga.setStoreId(store.getId());

        return store;
    }

    /**
     * Write an authorization model to a specific store.
     * The store must be set on the client before calling this method.
     */
    public WriteAuthorizationModelResponse writeAuthorizationModel(String storeId, WriteAuthorizationModelRequest request)
            throws ExecutionException, InterruptedException {
        // Set the store ID on the client
        fga.setStoreId(storeId);

        ClientWriteAuthorizationModelOptions opts = new ClientWriteAuthorizationModelOptions();
        WriteAuthorizationModelResponse response;
        try {
            response = fga.writeAuthorizationModel(request, opts).get();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }

        return response;
    }
}
