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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcUserActivity;
import io.gravitee.am.repository.management.api.UserActivityRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDateTime;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.domain.Sort.Direction.DESC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcUserActivityRepository extends AbstractJdbcRepository implements UserActivityRepository {

    public static final String COL_ID = "id";
    private static final String COL_REFERENCE_TYPE = "reference_type";
    private static final String COL_REFERENCE_ID = "reference_id";
    private static final String COL_USER_ACTIVITY_KEY = "user_activity_key";
    private static final String COL_USER_ACTIVITY_TYPE = "user_activity_type";
    private static final String COL_EXPIRE_AT = "expire_at";
    private static final String COL_CREATED_AT = "created_at";

    private UserActivity toEntity(JdbcUserActivity jdbcEntity) {
        return mapper.map(jdbcEntity, UserActivity.class);
    }

    private JdbcUserActivity toJdbcEntity(UserActivity jdbcEntity) {
        return mapper.map(jdbcEntity, JdbcUserActivity.class);
    }

    @Override
    public Maybe<UserActivity> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToMaybe(this.getTemplate().select(JdbcUserActivity.class)
                .matching(query(where(COL_ID).is(id).and(where(COL_EXPIRE_AT).greaterThanOrEquals(now))))
                .first())
                .map(this::toEntity);
    }

    @Override
    public Single<UserActivity> create(UserActivity item) {
        LOGGER.debug("create({})", item);
        final JdbcUserActivity entity = toJdbcEntity(item);
        entity.setId(entity.getId() == null ? RandomString.generate() : entity.getId());
        return monoToSingle(this.getTemplate().insert(entity)).map(this::toEntity);
    }

    @Override
    public Single<UserActivity> update(UserActivity item) {
        LOGGER.debug("update({})", item);
        final JdbcUserActivity entity = toJdbcEntity(item);
        return monoToSingle(this.getTemplate().update(entity)).map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(this.getTemplate().delete(JdbcUserActivity.class).matching(query(where(COL_ID).is(id))).all());
    }

    @Override
    public Flowable<UserActivity> findByReferenceAndTypeAndKeyAndLimit(ReferenceType referenceType, String referenceId, Type type, String key, int limit) {
        LOGGER.debug("findByReferenceAndKey({}, {}, {})", referenceType, referenceId, key);
        LocalDateTime now = LocalDateTime.now(UTC);
        var query = query(where(COL_REFERENCE_TYPE).is(referenceType)
                .and(where(COL_REFERENCE_ID).is(referenceId))
                .and(where(COL_USER_ACTIVITY_KEY).is(key))
                .and(where(COL_USER_ACTIVITY_TYPE).is(type.name()))
                .and(where(COL_EXPIRE_AT).greaterThanOrEquals(now))
        ).sort(Sort.by(DESC, COL_CREATED_AT));

        if (limit > 0) {
            query = query.limit(limit);
        }

        return fluxToFlowable(getTemplate().select(JdbcUserActivity.class).matching(query).all()).map(this::toEntity);
    }

    @Override
    public Completable deleteByReferenceAndKey(ReferenceType referenceType, String referenceId, String key) {
        LOGGER.debug("deleteByReferenceAndKey({}, {}, {})", referenceType, referenceId, key);
        return monoToCompletable(this.getTemplate().delete(JdbcUserActivity.class).matching(
                query(where(COL_REFERENCE_TYPE).is(referenceType)
                        .and(where(COL_REFERENCE_ID).is(referenceId))
                        .and(where(COL_USER_ACTIVITY_KEY).is(key)))).all());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("deleteByReference({}, {})", referenceType, referenceId);
        return monoToCompletable(this.getTemplate().delete(JdbcUserActivity.class).matching(
                query(where(COL_REFERENCE_TYPE).is(referenceType)
                        .and(where(COL_REFERENCE_ID).is(referenceId)))).all());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(
                getTemplate().delete(JdbcUserActivity.class).matching(Query.query(where(COL_EXPIRE_AT).lessThan(now))).all()
        ).doOnError(error -> LOGGER.error("Unable to purge Devices", error));
    }
}
