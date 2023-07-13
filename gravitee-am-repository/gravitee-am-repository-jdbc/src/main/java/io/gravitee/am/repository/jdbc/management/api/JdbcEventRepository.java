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
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcEvent;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringEventRepository;
import io.gravitee.am.repository.management.api.EventRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcEventRepository extends AbstractJdbcRepository implements EventRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_PAYLOAD = "payload";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_TYPE,
            COL_PAYLOAD,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

    @Autowired
    private SpringEventRepository eventRepository;

    protected Event toEntity(JdbcEvent entity) {
        return mapper.map(entity, Event.class);
    }

    protected JdbcEvent toJdbcEntity(Event entity) {
        return mapper.map(entity, JdbcEvent.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("events", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("events", columns, List.of(COL_ID));
    }

    @Override
    public Flowable<Event> findByTimeFrame(long from, long to) {
        LOGGER.debug("findByTimeFrame({}, {})", from, to);
        return eventRepository.findByTimeFrame(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(from), UTC),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(to), UTC))
                .map(this::toEntity);
    }

    @Override
    public Maybe<Event> findById(String id) {
        LOGGER.debug("findById({})", id);
        return eventRepository.findById(id).map(this::toEntity);
    }

    @Override
    public Single<Event> create(Event item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create event with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = template.getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TYPE, item.getType() == null ? null : item.getType().name(), String.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, COL_PAYLOAD, item.getPayload());
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<Event> update(Event item) {
        LOGGER.debug("update event with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec update = template.getDatabaseClient().sql(UPDATE_STATEMENT);
        update = addQuotedField(update, COL_ID, item.getId(), String.class);
        update = addQuotedField(update, COL_TYPE, item.getType() == null ? null : item.getType().name(), String.class);
        update = databaseDialectHelper.addJsonField(update, COL_PAYLOAD, item.getPayload());
        update = addQuotedField(update, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = update.fetch().rowsUpdated();
        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return eventRepository.deleteById(id);
    }
}
