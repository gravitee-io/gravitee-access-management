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
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcSystemTask;
import io.gravitee.am.repository.jdbc.management.api.model.mapper.LocalDateConverter;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcSystemTaskRepository extends AbstractJdbcRepository implements SystemTaskRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_STATUS = "status";
    public static final String COL_OPERATION_ID = "operation_id";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";
    public static final String COL_CONFIGURATION = "configuration";
    public static final String COL_KIND = "kind";
    public static final String WHERE_SUFFIX = "_where";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_TYPE,
            COL_STATUS,
            COL_OPERATION_ID,
            COL_CREATED_AT,
            COL_UPDATED_AT,
            COL_CONFIGURATION,
            COL_KIND
    );

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

    protected final LocalDateConverter dateConverter = new LocalDateConverter();

    protected SystemTask toEntity(JdbcSystemTask entity) {
        return mapper.map(entity, SystemTask.class);
    }

    protected JdbcSystemTask toJdbcEntity(SystemTask entity) {
        return mapper.map(entity, JdbcSystemTask.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("system_tasks", columns);
        // the operation_id used in the where clause may be different from the one present into the bean, so we append a suffix
        this.UPDATE_STATEMENT = createUpdateStatement("system_tasks", columns, List.of(COL_ID, COL_OPERATION_ID))+WHERE_SUFFIX;
    }

    @Override
    public Maybe<SystemTask> findById(String id) {
        LOGGER.debug("findById({}, {}, {})", id);
        return monoToMaybe(template.select(Query.query(where(COL_ID).is(id)).limit(1), JdbcSystemTask.class).singleOrEmpty())
                .map(this::toEntity);
    }

    @Override
    public Single<SystemTask> create(SystemTask item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create SystemTask with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = template.getDatabaseClient().sql(INSERT_STATEMENT);
        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TYPE, item.getType(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_STATUS, item.getStatus(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_OPERATION_ID, item.getOperationId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_CONFIGURATION, item.getConfiguration(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_KIND, item.getKind(), String.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();
        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<SystemTask> update(SystemTask item) {
        return Single.error(new IllegalStateException("SystemTask can't be updated without control on the operationId"));
    }

    @Override
    public Single<SystemTask> updateIf(SystemTask item, String operationId) {
        LOGGER.debug("Update SystemTask with id {} and operationId {}", item.getId(), operationId);

        DatabaseClient.GenericExecuteSpec updateSpec = template.getDatabaseClient().sql(UPDATE_STATEMENT);

        updateSpec = addQuotedField(updateSpec, COL_ID, item.getId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_TYPE, item.getType(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_STATUS, item.getStatus(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_OPERATION_ID, item.getOperationId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateSpec = addQuotedField(updateSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        updateSpec = addQuotedField(updateSpec, COL_CONFIGURATION, item.getConfiguration(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_KIND, item.getKind(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_OPERATION_ID + WHERE_SUFFIX, operationId, String.class);

        Mono<Long> action = updateSpec.fetch().rowsUpdated();
        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("Delete SystemTask with id {}", id);
        Mono<Integer> delete = template.delete(JdbcSystemTask.class)
                .matching(Query.query(where(COL_ID).is(id))).all();
        return monoToCompletable(delete);
    }

    @Override
    public Flowable<SystemTask> findByType(String type) {
        return fluxToFlowable(template.select(Query.query(where(COL_TYPE).is(type)), JdbcSystemTask.class))
                .map(this::toEntity);
    }
}
