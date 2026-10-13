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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity;

import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Credential storage kept in memory, so the WebAuthn ceremonies run against the real
 * {@link io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStorage}
 * and the test can read back exactly what AM would persist.
 *
 * @author GraviteeSource Team
 */
class InMemoryCredentialGatewayService implements CredentialGatewayService {

    private final Map<String, Credential> credentials = new ConcurrentHashMap<>();

    List<Credential> all() {
        return new ArrayList<>(credentials.values());
    }

    Credential byCredentialId(String credentialId) {
        return credentials.values().stream()
                .filter(credential -> Objects.equals(credentialId, credential.getCredentialId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No credential stored for credential id " + credentialId));
    }

    void put(Credential credential) {
        if (credential.getId() == null) {
            credential.setId(UUID.randomUUID().toString());
        }
        credentials.put(credential.getId(), credential);
    }

    @Override
    public Maybe<Credential> findById(Domain domain, String id) {
        return Maybe.fromOptional(java.util.Optional.ofNullable(credentials.get(id)));
    }

    @Override
    public Flowable<Credential> findByUsername(Domain domain, String username) {
        return Flowable.fromIterable(all()).filter(credential -> Objects.equals(username, credential.getUsername()));
    }

    @Override
    public Flowable<Credential> findByUsername(Domain domain, String username, int limit) {
        return findByUsername(domain, username).take(limit);
    }

    @Override
    public Flowable<Credential> findByCredentialId(Domain domain, String credentialId) {
        return Flowable.fromIterable(all()).filter(credential -> Objects.equals(credentialId, credential.getCredentialId()));
    }

    @Override
    public Flowable<Credential> findByUserId(Domain domain, String userId) {
        return Flowable.fromIterable(all()).filter(credential -> Objects.equals(userId, credential.getUserId()));
    }

    @Override
    public Single<Credential> create(Domain domain, Credential credential) {
        put(credential);
        return Single.just(credential);
    }

    @Override
    public Single<Credential> update(Domain domain, Credential credential) {
        put(credential);
        return Single.just(credential);
    }

    @Override
    public Single<Credential> update(Domain domain, String credentialId, Credential credential) {
        return update(domain, credential);
    }

    @Override
    public Completable delete(Domain domain, String id) {
        return Completable.fromRunnable(() -> credentials.remove(id));
    }

    @Override
    public Completable delete(Domain domain, String id, boolean enforceFactorDelete) {
        return delete(domain, id);
    }

    @Override
    public Completable deleteByUserId(Domain domain, String userId) {
        return Completable.fromRunnable(() -> credentials.values().removeIf(credential -> Objects.equals(userId, credential.getUserId())));
    }

    @Override
    public Completable deleteByDomain(Domain domain) {
        return Completable.fromRunnable(credentials::clear);
    }
}
