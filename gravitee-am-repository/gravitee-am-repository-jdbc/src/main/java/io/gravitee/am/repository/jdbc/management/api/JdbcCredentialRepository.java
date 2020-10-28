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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcCredential;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringCredentialRepository;
import io.gravitee.am.repository.management.api.CredentialRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;

import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcCredentialRepository extends AbstractJdbcRepository implements CredentialRepository {
    @Autowired
    private SpringCredentialRepository credentialRepository;

    protected Credential toEntity(JdbcCredential entity) {
        return mapper.map(entity, Credential.class);
    }

    protected JdbcCredential toJdbcEntity(Credential entity) {
        return mapper.map(entity, JdbcCredential.class);
    }

    @Override
    public Single<List<Credential>> findByUserId(ReferenceType referenceType, String referenceId, String userId) {
        LOGGER.debug("findByUserId({},{},{})", referenceType, referenceId, userId);
        return credentialRepository.findByUserId(referenceType.name(), referenceId, userId)
                .map(this::toEntity)
                .toList()
                .doOnError(error -> LOGGER.error("Unable to retrieve credentials for refId={}, refType={}, userId={}",
                        referenceId, referenceType, userId, error));
    }

    @Override
    public Single<List<Credential>> findByUsername(ReferenceType referenceType, String referenceId, String username) {
        LOGGER.debug("findByUsername({},{},{})", referenceType, referenceId, username);
        return credentialRepository.findByUsername(referenceType.name(), referenceId, username)
                .map(this::toEntity)
                .toList()
                .doOnError(error -> LOGGER.error("Unable to retrieve credentials for refId={}, refType={}, username={}",
                        referenceId, referenceType, username, error));
    }

    @Override
    public Single<List<Credential>> findByCredentialId(ReferenceType referenceType, String referenceId, String credentialId) {
        LOGGER.debug("findByCredentialId({},{},{})", referenceType, referenceId, credentialId);
        return credentialRepository.findByCredentialId(referenceType.name(), referenceId, credentialId)
                .map(this::toEntity)
                .toList()
                .doOnError(error -> LOGGER.error("Unable to retrieve credentials for refId={}, refType={}, credential={}",
                        referenceId, referenceType, credentialId, error));

    }

    @Override
    public Maybe<Credential> findById(String id) {
        LOGGER.debug("findById({})", id);
        return credentialRepository.findById(id)
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve credential for Id {}", id, error));
    }

    @Override
    public Single<Credential> create(Credential item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create credential with id {}", item.getId());

        Mono<Integer> action = dbClient.insert()
                .into(JdbcCredential.class)
                .using(toJdbcEntity(item))
                .fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create credential with id {}", item.getId(), error));
    }

    @Override
    public Single<Credential> update(Credential item) {
        LOGGER.debug("update credential with id {}", item.getId());
        return this.credentialRepository.save(toJdbcEntity(item))
                .map(this::toEntity)
                .doOnError((error) -> LOGGER.error("unable to create credential with id {}", item.getId(), error));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return credentialRepository.deleteById(id)
                .doOnError(error -> LOGGER.error("Unable to delete credential for Id {}", id, error));

    }
}
