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
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthenticationDeviceNotifier;
import io.gravitee.am.repository.management.api.AuthenticationDeviceNotifierRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAuthenticationDeviceNotifierRepository extends AbstractJdbcRepository implements AuthenticationDeviceNotifierRepository {

    public static final String REFERENCE_ID_FIELD = "reference_id";
    public static final String REF_TYPE_FIELD = "reference_type";
    public static final String ID_FIELD = "id";

    protected AuthenticationDeviceNotifier toEntity(JdbcAuthenticationDeviceNotifier entity) {
        return mapper.map(entity, AuthenticationDeviceNotifier.class);
    }

    protected JdbcAuthenticationDeviceNotifier toJdbcEntity(AuthenticationDeviceNotifier entity) {
        return mapper.map(entity, JdbcAuthenticationDeviceNotifier.class);
    }

    @Override
    public Flowable<AuthenticationDeviceNotifier> findAll() {
        LOGGER.debug("findAll()");
        return fluxToFlowable(getTemplate().select(JdbcAuthenticationDeviceNotifier.class)
                .all())
                .map(this::toEntity);
    }

    @Override
    public Flowable<AuthenticationDeviceNotifier> findByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({}, {})", referenceType, referenceId);
        return fluxToFlowable(getTemplate().select(Query.query(from(where(REFERENCE_ID_FIELD).is(referenceId).and(where(REF_TYPE_FIELD).is(referenceType.name())))),
                        JdbcAuthenticationDeviceNotifier.class))
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthenticationDeviceNotifier> findById(String id) {
        LOGGER.debug("findById({})", id);
        return monoToMaybe(getTemplate().select(Query.query(from(where(ID_FIELD).is(id))),JdbcAuthenticationDeviceNotifier.class)
                .singleOrEmpty())
                .map(this::toEntity);
    }

    @Override
    public Single<AuthenticationDeviceNotifier> create(AuthenticationDeviceNotifier item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create authentication device notifier with id {}", item.getId());
        return monoToSingle(getTemplate().insert(this.toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<AuthenticationDeviceNotifier> update(AuthenticationDeviceNotifier item) {
        LOGGER.debug("update authentication device notifier with id {}", item.getId());
        return monoToSingle(getTemplate().update(this.toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(getTemplate().delete(Query.query(from(where(ID_FIELD).is(id))), JdbcAuthenticationDeviceNotifier.class));
    }
}
