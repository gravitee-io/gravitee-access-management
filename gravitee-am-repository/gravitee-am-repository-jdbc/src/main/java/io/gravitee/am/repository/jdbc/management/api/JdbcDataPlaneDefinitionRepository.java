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

import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcDataPlaneDefinition;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.CustomLog;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author GraviteeSource Team
 */
@Repository
@CustomLog
public class JdbcDataPlaneDefinitionRepository extends AbstractJdbcRepository implements DataPlaneDefinitionRepository, InitializingBean {

    private static final String TABLE = "dataplanes";
    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_TYPE = "type";
    private static final String COL_GATEWAY_URL = "gateway_url";
    private static final String COL_ORGANIZATION_ID = "organization_id";
    private static final String COL_ENVIRONMENT_ID = "environment_id";
    private static final String COL_CONFIGURATION = "configuration";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> COLUMNS = List.of(
            COL_ID,
            COL_NAME,
            COL_TYPE,
            COL_GATEWAY_URL,
            COL_ORGANIZATION_ID,
            COL_ENVIRONMENT_ID,
            COL_CONFIGURATION,
            COL_CREATED_AT,
            COL_UPDATED_AT);

    private String insertStatement;
    private String updateStatement;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement(TABLE, COLUMNS);
        this.updateStatement = createUpdateStatement(TABLE, COLUMNS, List.of(COL_ID));
    }

    @Override
    public Flowable<DataPlaneDefinition> findAll() {
        log.debug("findAll()");
        return findAll(Query.empty(), JdbcDataPlaneDefinition.class)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<DataPlaneDefinition> findById(String id) {
        log.debug("findById({})", id);
        return findOne(Query.query(where(COL_ID).is(id)), JdbcDataPlaneDefinition.class)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<DataPlaneDefinition> findByEnvironmentId(String environmentId) {
        log.debug("findByEnvironmentId({})", environmentId);
        return findAll(Query.query(where(COL_ENVIRONMENT_ID).is(environmentId)), JdbcDataPlaneDefinition.class)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DataPlaneDefinition> create(DataPlaneDefinition item) {
        log.debug("create data plane definition {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertStatement);
        insertSpec = addFields(insertSpec, item);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();
        return monoToSingle(action)
                .flatMap(i -> findById(item.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DataPlaneDefinition> update(DataPlaneDefinition item) {
        log.debug("update data plane definition {}", item.getId());

        DatabaseClient.GenericExecuteSpec update = getTemplate().getDatabaseClient().sql(updateStatement);
        update = addFields(update, item);

        Mono<Long> action = update.fetch().rowsUpdated();
        return monoToSingle(action)
                .flatMap(i -> findById(item.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        log.debug("delete data plane definition {}", id);
        Mono<Long> delete = getTemplate().delete(JdbcDataPlaneDefinition.class)
                .matching(Query.query(where(COL_ID).is(id)))
                .all();
        return monoToCompletable(delete)
                .observeOn(Schedulers.computation());
    }

    private DatabaseClient.GenericExecuteSpec addFields(DatabaseClient.GenericExecuteSpec spec, DataPlaneDefinition item) {
        spec = addQuotedField(spec, COL_ID, item.getId(), String.class);
        spec = addQuotedField(spec, COL_NAME, item.getName(), String.class);
        spec = addQuotedField(spec, COL_TYPE, item.getType(), String.class);
        spec = addQuotedField(spec, COL_GATEWAY_URL, item.getGatewayUrl(), String.class);
        spec = addQuotedField(spec, COL_ORGANIZATION_ID, item.getOrganizationId(), String.class);
        spec = addQuotedField(spec, COL_ENVIRONMENT_ID, item.getEnvironmentId(), String.class);
        spec = addQuotedField(spec, COL_CONFIGURATION, item.getConfiguration(), String.class);
        spec = addQuotedField(spec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        spec = addQuotedField(spec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        return spec;
    }

    private DataPlaneDefinition toEntity(JdbcDataPlaneDefinition entity) {
        if (entity == null) {
            return null;
        }
        DataPlaneDefinition definition = new DataPlaneDefinition();
        definition.setId(entity.getId());
        definition.setName(entity.getName());
        definition.setType(entity.getType());
        definition.setGatewayUrl(entity.getGatewayUrl());
        definition.setOrganizationId(entity.getOrganizationId());
        definition.setEnvironmentId(entity.getEnvironmentId());
        definition.setConfiguration(entity.getConfiguration());
        definition.setCreatedAt(dateConverter.convertFrom(entity.getCreatedAt(), null));
        definition.setUpdatedAt(dateConverter.convertFrom(entity.getUpdatedAt(), null));
        return definition;
    }
}
