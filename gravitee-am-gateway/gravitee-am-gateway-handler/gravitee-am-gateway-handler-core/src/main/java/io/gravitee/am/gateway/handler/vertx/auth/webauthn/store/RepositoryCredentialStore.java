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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.store;

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.CredentialService;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.CredentialStore;
import io.vertx.reactivex.core.ObservableHelper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RepositoryCredentialStore implements CredentialStore {

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private Domain domain;

    @Override
    public CredentialStore getUserCredentialsByName(String username, Handler<AsyncResult<List<JsonObject>>> handler) {
        credentialService
                .findByUsername(ReferenceType.DOMAIN, domain.getId(), username)
                .map(credentials -> credentials.stream().map(this::toJson).collect(Collectors.toList()))
                .subscribe(
                        credentials -> handler.handle(Future.succeededFuture(credentials)),
                        error -> handler.handle(Future.failedFuture(error))
                );
        return this;
    }

    @Override
    public CredentialStore getUserCredentialsById(String id, Handler<AsyncResult<List<JsonObject>>> handler) {
        credentialService
                .findByCredentialId(ReferenceType.DOMAIN, domain.getId(), id)
                .map(credentials -> credentials.stream().map(this::toJson).collect(Collectors.toList()))
                .subscribe(
                        credentials -> handler.handle(Future.succeededFuture(credentials)),
                        error -> handler.handle(Future.failedFuture(error))
                );
        return this;
    }

    @Override
    public CredentialStore updateUserCredential(String id, JsonObject data, boolean upsert, Handler<AsyncResult<Void>> handler) {
        credentialService.findByCredentialId(ReferenceType.DOMAIN, domain.getId(), id)
                .flatMapObservable(credentials -> Observable.fromIterable(credentials))
                .flatMapSingle(credential -> {
                    credential.setPublicKey(data.getString("publicKey"));
                    credential.setCounter(data.getLong("counter", 0L));
                    credential.setUpdatedAt(new Date());
                    return credentialService.update(credential);
                })
                .toList()
                .flatMapCompletable(credentials -> {
                    if (!credentials.isEmpty()) {
                        return Completable.complete();
                    }
                    if (!upsert) {
                        return Completable.error(new IllegalStateException("Nothing updated!"));
                    }
                    // no credential found, create it
                    Credential credential = new Credential();
                    credential.setReferenceType(ReferenceType.DOMAIN);
                    credential.setReferenceId(domain.getId());
                    credential.setUserId(data.getString("userId"));
                    credential.setUsername(data.getString("username"));
                    credential.setCredentialId(data.getString("credID"));
                    credential.setPublicKey(data.getString("publicKey"));
                    credential.setCounter(data.getLong("counter", 0L));
                    credential.setCreatedAt(new Date());
                    credential.setUpdatedAt(credential.getCreatedAt());
                    return credentialService.create(credential).ignoreElement();
                })
                .subscribe(
                        () ->  handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error.getMessage()))
                );
        return this;
    }

    private JsonObject toJson(Credential credential) {
        return new JsonObject()
                .put("credID", credential.getCredentialId())
                .put("publicKey", credential.getPublicKey())
                .put("counter", credential.getCounter());
    }
}
