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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

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
    public Flowable<Credential> findByUserId(ReferenceType referenceType, String referenceId, String userId) {
        LOGGER.debug("findByUserId({},{},{})", referenceType, referenceId, userId);
        return credentialRepository.findByUserId(referenceType.name(), referenceId, userId)
                .map(this::toEntity);
    }

    @Override
    public Flowable<Credential> findByUsername(ReferenceType referenceType, String referenceId, String username) {
        LOGGER.debug("findByUsername({},{},{})", referenceType, referenceId, username);
        return credentialRepository.findByUsername(referenceType.name(), referenceId, username)
                .map(this::toEntity);
    }

    @Override
    public Flowable<Credential> findByCredentialId(ReferenceType referenceType, String referenceId, String credentialId) {
        LOGGER.debug("findByCredentialId({},{},{})", referenceType, referenceId, credentialId);
        return credentialRepository.findByCredentialId(referenceType.name(), referenceId, credentialId)
                .map(this::toEntity);

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
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
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

    @Override
    public Completable deleteByUserId(ReferenceType referenceType, String referenceId, String userId) {
        LOGGER.debug("deleteByUserId({})", userId);
        return monoToCompletable(getTemplate().delete(JdbcCredential.class)
                .matching(Query.query(
                        where("reference_type").is(referenceType.name())
                                .and(where("reference_id").is(referenceId))
                                .and(where("user_id").is(userId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete credential for userId {}", userId, error));
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("deleteByReference({} - {})", referenceType.name(), referenceId);
        return monoToCompletable(getTemplate().delete(JdbcCredential.class)
                .matching(Query.query(
                        where("reference_type").is(referenceType.name())
                                .and(where("reference_id").is(referenceId))))
                .all())
                .doOnError(error -> LOGGER.error("Unable to delete credential for reference {} - {}", referenceType.name(), referenceId, error));
    }
}
