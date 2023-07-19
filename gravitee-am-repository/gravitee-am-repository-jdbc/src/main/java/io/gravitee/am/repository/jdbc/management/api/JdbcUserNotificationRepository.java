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
import io.gravitee.am.model.notification.UserNotification;
import io.gravitee.am.model.notification.UserNotificationStatus;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcUserNotification;
import io.gravitee.am.repository.management.api.UserNotificationRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcUserNotificationRepository extends AbstractJdbcRepository implements UserNotificationRepository {

    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_STATUS = "status";
    public static final String COL_AUDIENCE = "audience";
    public static final String COL_CREATED_AT = "created_at";
    public static final int NOTIFICATION_LIMIT = 20;

    protected UserNotification toEntity(JdbcUserNotification entity) {
        return mapper.map(entity, UserNotification.class);
    }

    protected JdbcUserNotification toJdbcEntity(UserNotification entity) {
        return mapper.map(entity, JdbcUserNotification.class);
    }

    @Override
    public Maybe<UserNotification> findById(String id) {
        LOGGER.debug("findById({})", id);
        return monoToMaybe(this.getTemplate().select(JdbcUserNotification.class)
                .matching(query(where(COL_ID).is(id)))
                .first())
                .map(this::toEntity);
    }

    @Override
    public Single<UserNotification> create(UserNotification item) {
        LOGGER.debug("create({})", item);
        final JdbcUserNotification entity = toJdbcEntity(item);
        entity.setId(entity.getId() == null ? RandomString.generate() : entity.getId());
        return monoToSingle(this.getTemplate().insert(entity)).map(this::toEntity);
    }

    @Override
    public Single<UserNotification> update(UserNotification item) {
        LOGGER.debug("update({})", item);
        final JdbcUserNotification entity = toJdbcEntity(item);
        return monoToSingle(this.getTemplate().update(entity)).map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(this.getTemplate().delete(JdbcUserNotification.class).matching(query(where(COL_ID).is(id))).all());
    }

    @Override
    public Flowable<UserNotification> findAllByAudienceAndStatus(String audience, UserNotificationStatus status) {
        LOGGER.debug("findAllByAudienceAndStatus({}, {})", audience, status);
        return fluxToFlowable(getTemplate().select(JdbcUserNotification.class).matching(query(
                where(COL_AUDIENCE).is(audience)
                        .and(where(COL_STATUS).is(status.name())))
                        .sort(Sort.by(COL_CREATED_AT))
                        .limit(NOTIFICATION_LIMIT))
                .all()).map(this::toEntity);
    }

    @Override
    public Completable updateNotificationStatus(String id, UserNotificationStatus status) {
        return monoToCompletable(getTemplate().getDatabaseClient().sql("UPDATE user_notifications SET updated_at = :update, status = :status WHERE id = :id")
                .bind("update", LocalDateTime.now(ZoneOffset.UTC))
                .bind("status", status.name())
                .bind("id", id).fetch().rowsUpdated());
    }
}
