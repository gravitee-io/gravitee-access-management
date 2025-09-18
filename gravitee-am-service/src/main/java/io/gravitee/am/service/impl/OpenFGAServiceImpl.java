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
package io.gravitee.am.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientReadChangesRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.configuration.ClientListStoresOptions;
import dev.openfga.sdk.api.configuration.ClientReadChangesOptions;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.ListStoresResponse;
import dev.openfga.sdk.api.model.ReadChangesResponse;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import io.gravitee.am.service.OpenFGAService;
import io.gravitee.am.service.model.OpenFGAAuthenticationModel;
import io.gravitee.am.service.model.OpenFGACheckResponse;
import io.gravitee.am.service.model.OpenFGAConnectResponse;
import io.gravitee.am.service.model.OpenFGAStoreEntity;
import io.gravitee.am.service.model.OpenFGATuple;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author GraviteeSource Team
 */
@Component
public class OpenFGAServiceImpl implements OpenFGAService {

    private static final Logger logger = LoggerFactory.getLogger(OpenFGAServiceImpl.class);

    private OpenFgaClient client;

    @Override
    public Single<OpenFGAConnectResponse> connect(String serverUrl) {
        return Single.fromCallable(() -> {
            try {
                logger.info("Connecting OpenFGA server: {}", serverUrl);

                // Create OpenFGA client configuration
                var config = new ClientConfiguration()
                    .apiUrl(serverUrl);

                // Create OpenFGA client
                client = new OpenFgaClient(config);

                // Test connection by trying to list stores
                List<OpenFGAStoreEntity> stores = client.listStores().get().getStores().stream().map(OpenFGAStoreEntity::fromStore).toList();

                logger.info("Successfully connected to OpenFGA server: {}", serverUrl);
                logger.info("Found {} stores", stores.size());

                return new OpenFGAConnectResponse(serverUrl, true, stores);
            } catch (Exception e) {
                logger.warn("Failed to connect to OpenFGA server: {} - {}", serverUrl, e.getMessage());
                logger.debug("Connection error details", e);
                return new OpenFGAConnectResponse(serverUrl, false, null);
            }
        });
    }

    @Override
    public Flowable<OpenFGAStoreEntity> getStores() {
            ClientListStoresOptions opts = new ClientListStoresOptions();
                opts.pageSize(10);
            try {
                return Flowable
                        .fromFuture(client.listStores(opts))
                        .flatMapIterable(ListStoresResponse::getStores)
                        .map(store -> new OpenFGAStoreEntity(store.getId(), store.getName(), Date.from(store.getCreatedAt().toInstant()),Date.from(store.getUpdatedAt().toInstant())));
            } catch (FgaInvalidParameterException e) {
                throw new RuntimeException(e);
            }
    }

    @Override
    public Single<OpenFGAStoreEntity> createStore(String storeName) throws FgaInvalidParameterException {
        CreateStoreRequest req = new CreateStoreRequest().name(storeName);

        return Single
                .fromFuture(client.createStore(req))
                .map(resp -> new OpenFGAStoreEntity(
                        resp.getId(),
                        resp.getName() != null ? resp.getName() : storeName,
                        null,
                        null
                ))
                .onErrorResumeNext(err -> Single.error(wrap(err)));
    }

    @Override
    public Single<OpenFGAAuthenticationModel> setAuthenticationModel(String storeId, String modelDsl)
            throws JsonProcessingException, FgaInvalidParameterException {
        client.setStoreId(storeId);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        WriteAuthorizationModelRequest req = mapper.readValue(modelDsl, WriteAuthorizationModelRequest.class);

        return Single
                .fromFuture(client.writeAuthorizationModel(req))
                .map(resp -> new OpenFGAAuthenticationModel(resp.getRawResponse()))
                .onErrorResumeNext(err -> Single.error(wrap(err)));
    }

    @Override
    public Single<String> getAuthorizationModel(String storeId) throws FgaInvalidParameterException {
        client.setStoreId(storeId);
        return Single
                .fromFuture(client.readLatestAuthorizationModel())
                .map(resp -> {
                    if (resp.getAuthorizationModel() != null && resp.getAuthorizationModel().getTypeDefinitions() != null) {
                        // Convert the authorization model to JSON string
                        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
                        try {
                            return mapper.writeValueAsString(resp.getAuthorizationModel());
                        } catch (Exception e) {
                            logger.error("Failed to serialize authorization model", e);
                            return "{}";
                        }
                    }
                    return "{}";
                })
                .onErrorResumeNext(err -> {
                    logger.warn("Failed to read authorization model for store {}: {}", storeId, err.getMessage());
                    return Single.just("{}");
                });
    }

    @Override
    public Flowable<OpenFGATuple> getTuples(String storeId) throws FgaInvalidParameterException {
        client.setStoreId(storeId);
        var options = new ClientReadChangesOptions()
                .pageSize(25);

        var body = new ClientReadChangesRequest();
        return Flowable
                .fromFuture(client.readChanges(body, options))
                .flatMapIterable(ReadChangesResponse::getChanges)
                .map(ch -> OpenFGATuple.fromTupleKey(ch.getTupleKey()));
    }


    @Override
    public Single<OpenFGATuple> createTuple(String storeId, OpenFGATuple tuple) throws FgaInvalidParameterException {
        client.setStoreId(storeId);

        ClientTupleKey tk = new ClientTupleKey()
                .user(Objects.requireNonNull(tuple.getUser(), "tuple.user is required"))
                .relation(Objects.requireNonNull(tuple.getRelation(), "tuple.relation is required"))
                ._object(Objects.requireNonNull(tuple.getObject(), "tuple.object is required"));

        ClientWriteRequest req = new ClientWriteRequest().writes(List.of(tk));

        return Single
                .fromFuture(client.write(req))
                .map(ignore -> tuple)
                .onErrorResumeNext(err -> Single.error(wrap(err)));
    }

    @Override
    public Single<OpenFGACheckResponse> checkPermission(String storeId, OpenFGATuple tuple) throws FgaInvalidParameterException {
        client.setStoreId(storeId);

        ClientCheckRequest req = new ClientCheckRequest()
                .user(Objects.requireNonNull(tuple.getUser(), "tuple.user is required"))
                .relation(Objects.requireNonNull(tuple.getRelation(), "tuple.relation is required"))
                ._object(Objects.requireNonNull(tuple.getObject(), "tuple.object is required"));

        return Single
                .fromFuture(client.check(req))
                .map(resp -> {
                    OpenFGACheckResponse checkResponse = new OpenFGACheckResponse();
                    checkResponse.setAllowed(Boolean.TRUE.equals(resp.getAllowed()));
                    checkResponse.setUser(tuple.getUser());
                    checkResponse.setRelation(tuple.getRelation());
                    checkResponse.setObject(tuple.getObject());
                    checkResponse.setResolution(resp.getResolution() != null ? resp.getResolution().toString() : "");
                    return checkResponse;
                })
                .onErrorResumeNext(err -> Single.error(wrap(err)));
    }

    private Throwable wrap(Throwable t) {
        return (t instanceof FgaInvalidParameterException) ? t : new RuntimeException(t);
    }
}