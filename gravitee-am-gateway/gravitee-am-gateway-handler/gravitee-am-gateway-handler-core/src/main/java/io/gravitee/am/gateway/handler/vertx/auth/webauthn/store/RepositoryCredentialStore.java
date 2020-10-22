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
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.auth.webauthn.Authenticator;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RepositoryCredentialStore {

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private Domain domain;

    public Future<List<Authenticator>> fetch(Authenticator query) {
        Promise<List<Authenticator>> promise = Promise.promise();

        Single<List<Credential>> fetchCredentials = query.getUserName() != null ?
                credentialService.findByUsername(ReferenceType.DOMAIN, domain.getId(), query.getUserName()) :
                credentialService.findByCredentialId(ReferenceType.DOMAIN, domain.getId(), query.getCredID());

        fetchCredentials
                .map(credentials -> credentials.stream().map(this::convert).collect(Collectors.toList()))
                .subscribe(
                        authenticators -> promise.complete(authenticators),
                        error -> promise.fail(error)
                );

        return promise.future();
    }

    public Future<Void> store(Authenticator authenticator) {
        Promise<Void> promise = Promise.promise();

        credentialService.findByCredentialId(ReferenceType.DOMAIN, domain.getId(), authenticator.getCredID())
                .flatMapObservable(credentials -> Observable.fromIterable(credentials))
                .flatMapSingle(credential -> {
                    credential.setCounter(authenticator.getCounter());
                    credential.setUpdatedAt(new Date());
                    return credentialService.update(credential);
                })
                .toList()
                .flatMapCompletable(credentials -> {
                    if (!credentials.isEmpty()) {
                        return Completable.complete();
                    }
                    // no credential found, create it
                    Credential credential = new Credential();
                    credential.setReferenceType(ReferenceType.DOMAIN);
                    credential.setReferenceId(domain.getId());
                    credential.setUsername(authenticator.getUserName());
                    credential.setCredentialId(authenticator.getCredID());
                    credential.setPublicKey(authenticator.getPublicKey());
                    credential.setCounter(authenticator.getCounter());
                    credential.setCreatedAt(new Date());
                    credential.setUpdatedAt(credential.getCreatedAt());
                    return credentialService.create(credential).ignoreElement();
                })
                .subscribe(
                        () ->  promise.complete(),
                        error -> promise.fail(error.getMessage())
                );
        return promise.future();
    }

    private Authenticator convert(Credential credential) {
        if (credential == null) {
            return null;
        }
        Authenticator authenticator = new Authenticator();
        authenticator.setUserName(credential.getUsername());
        authenticator.setCredID(credential.getCredentialId());
        authenticator.setCounter(credential.getCounter());
        authenticator.setPublicKey(credential.getPublicKey());

        return authenticator;
    }
}
