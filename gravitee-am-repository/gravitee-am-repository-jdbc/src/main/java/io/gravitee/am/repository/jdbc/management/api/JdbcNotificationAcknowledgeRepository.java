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
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcNotificationAcknowledge;
import io.gravitee.node.api.notifier.NotificationAcknowledge;
import io.gravitee.node.api.notifier.NotificationAcknowledgeRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.stereotype.Repository;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcNotificationAcknowledgeRepository extends AbstractJdbcRepository implements NotificationAcknowledgeRepository {

    public static final String COL_ID = "id";
    public static final String COL_RESOURCE = "resource_id";
    public static final String COL_RESOURCE_TYPE = "resource_type";
    public static final String COL_AUDIENCE = "audience_id";
    public static final String COL_TYPE = "type";

    protected NotificationAcknowledge toEntity(JdbcNotificationAcknowledge entity) {
        return mapper.map(entity, NotificationAcknowledge.class);
    }

    protected JdbcNotificationAcknowledge toJdbcEntity(NotificationAcknowledge entity) {
        return mapper.map(entity, JdbcNotificationAcknowledge.class);
    }

    @Override
    public Maybe<NotificationAcknowledge> findById(String id) {
        LOGGER.debug("findById({})", id);
        return monoToMaybe(this.getTemplate().select(JdbcNotificationAcknowledge.class)
                .matching(query(where(COL_ID).is(id)))
                .first())
                .map(this::toEntity);
    }

    @Override
    public Maybe<NotificationAcknowledge> findByResourceIdAndTypeAndAudienceId(String resourceId, String resourceType, String type, String audience) {
        LOGGER.debug("findByResourceIdAndAudienceId({},{},{},{})", resourceId, resourceType, type, audience);
        return monoToMaybe(this.getTemplate().select(JdbcNotificationAcknowledge.class)
                .matching(query(where(COL_RESOURCE).is(resourceId)
                        .and(where(COL_RESOURCE_TYPE).is(resourceType))
                        .and(where(COL_TYPE).is(type))
                        .and(where(COL_AUDIENCE).is(audience))))
                .first())
                .map(this::toEntity);
    }

    @Override
    public Single<NotificationAcknowledge> create(NotificationAcknowledge notificationAcknowledge) {
        LOGGER.debug("create({})", notificationAcknowledge);
        final JdbcNotificationAcknowledge entity = toJdbcEntity(notificationAcknowledge);
        entity.setId(entity.getId() == null ? RandomString.generate() : entity.getId());
        return monoToSingle(this.getTemplate().insert(entity)).map(this::toEntity);
    }

    @Override
    public Single<NotificationAcknowledge> update(NotificationAcknowledge notificationAcknowledge) {
        LOGGER.debug("update({})", notificationAcknowledge);
        final JdbcNotificationAcknowledge entity = toJdbcEntity(notificationAcknowledge);
        return monoToSingle(this.getTemplate().update(entity)).map(this::toEntity);
    }

    @Override
    public Completable deleteByResourceId(String resourceId, String resourceType) {
        LOGGER.debug("deleteByResourceId({}, {})", resourceId, resourceType);
        return monoToCompletable(this.getTemplate().delete(JdbcNotificationAcknowledge.class)
                .matching(query(where(COL_RESOURCE).is(resourceId).and(where(COL_RESOURCE_TYPE).is(resourceType))))
                .all());
    }

}
