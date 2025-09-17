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
import dev.openfga.sdk.api.client.model.ClientReadChangesRequest;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.configuration.ClientListStoresOptions;
import dev.openfga.sdk.api.configuration.ClientReadChangesOptions;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.ListStoresResponse;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import io.gravitee.am.service.OpenFGAService;
import io.gravitee.am.service.model.OpenFGAAuthenticationModel;
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
                opts.pageSize(1);
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
                .map(resp -> {
                    return new OpenFGAStoreEntity(
                            resp.getId(),
                            resp.getName() != null ? resp.getName() : storeName,
                            null,
                            null
                    );
                })
                .onErrorResumeNext(err -> Single.error(wrap(err)));
    }

    @Override
    public Single<OpenFGAAuthenticationModel> setAuthenticationModel(String storeId, String modelDsl)
            throws JsonProcessingException, FgaInvalidParameterException {
        client.setStoreId(storeId);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        // 1) DSL -> JSON (schema 1.1)
//        ObjectNode authModelJson = OpenFgaDslConverter.toAuthorizationModelJson(modelDsl, mapper);

        // 2) JSON -> WriteAuthorizationModelRequest
        WriteAuthorizationModelRequest req = mapper.readValue(modelDsl, WriteAuthorizationModelRequest.class);

        // 3) call client
        return Single
                .fromFuture(client.writeAuthorizationModel(req))
                .map(resp -> new OpenFGAAuthenticationModel(resp.getRawResponse()))
                .onErrorResumeNext(err -> Single.error(wrap(err)));
    }

    @Override
    public Flowable<OpenFGATuple> getTuples() throws FgaInvalidParameterException {
        var options = new ClientReadChangesOptions()
                .pageSize(25);

        var body = new ClientReadChangesRequest();
        var request = new ClientReadRequest();
        client.read(request);
        return Flowable.just(new OpenFGATuple());
//
//        var response = client.readChanges(body, options).get();
//        return paginateTuples(null)
//                .map(this::toDomainTuple)
//                .onErrorResumeNext(err -> Flowable.error(wrap(err)));
    }

//    private Flowable<Tuple> paginateTuples(String continuationToken) {
//        ClientReadRequest req = new ReadRequest();
//        if (continuationToken != null && !continuationToken.isEmpty()) {
//            req.setContinuationToken(continuationToken);
//        }
//
//        return Single.fromFuture(client.read(req))
//                .toFlowable()
//                .concatMap(resp -> {
//                    Flowable<Tuple> current = Flowable.fromIterable(resp.getTuples());
//                    String next = resp.getContinuationToken();
//                    if (next == null || next.isEmpty()) return current;
//                    return current.concatWith(Flowable.defer(() -> paginateTuples(next)));
//                });
//    }

    @Override
    public Single<OpenFGATuple> createTuple(OpenFGATuple tuple) throws FgaInvalidParameterException {
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

//    // ---------------------------
//    // CHECK (Authorization check)
//    // ---------------------------
//    @Override
//    public Single<OpenFGACheckResponse> check(OpenFGATuple tuple) {
//        // Check: “czy user ma relation do object”
//        CheckRequest req = new CheckRequest()
//                .tupleKey(new TupleKey()
//                        .user(Objects.requireNonNull(tuple.getUser(), "tuple.user is required"))
//                        .relation(Objects.requireNonNull(tuple.getRelation(), "tuple.relation is required"))
//                        .object(Objects.requireNonNull(tuple.getObject(), "tuple.object is required"))
//                );
//
//        return Single
//                .fromFuture(client.check(req))
//                .map(resp -> {
//                    OpenFGACheckResponse out = new OpenFGACheckResponse();
//                    out.setAllowed(Boolean.TRUE.equals(resp.getAllowed()));
//                    out.setResolution(resp.getResolution()); // jeśli Twoja klasa ma takie pole; w razie czego usuń
//                    return out;
//                })
//                .onErrorResumeNext(err -> Single.error(wrap(err)));
//    }

//    // ===========================
//    // HELPERY
//    // ===========================
//    private Date toDateSafe(OffsetDateTime odt) {
//        return odt == null ? null : Date.from(odt.toInstant());
//    }

    private Throwable wrap(Throwable t) {
        return (t instanceof FgaInvalidParameterException) ? t : new RuntimeException(t);
    }

    private OpenFGATuple toDomainTuple(Tuple t) {
        OpenFGATuple out = new OpenFGATuple();
        if (t.getKey() != null) {
            out.setUser(t.getKey().getUser());
            out.setRelation(t.getKey().getRelation());
            out.setObject(t.getKey().getObject());
        }
        // jeśli w Twoim modelu są dodatkowe pola (timestamp, trace), ustaw je tutaj
        return out;
    }
}