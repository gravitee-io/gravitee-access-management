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
import io.gravitee.am.model.AuthorizationSchema;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthorizationSchema;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringAuthorizationSchemaRepository;
import io.gravitee.am.repository.management.api.AuthorizationSchemaRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
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
public class JdbcAuthorizationSchemaRepository extends AbstractJdbcRepository implements AuthorizationSchemaRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_DOMAIN_ID = "domain_id";
    public static final String COL_ENGINE_TYPE = "engine_type";
    public static final String COL_CONTENT = "content";
    public static final String COL_VERSION = "version";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_DOMAIN_ID,
            COL_ENGINE_TYPE,
            COL_CONTENT,
            COL_VERSION,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    private String insertStatement;
    private String updateStatement;

    @Autowired
    private SpringAuthorizationSchemaRepository authorizationSchemaRepository;

    protected AuthorizationSchema toEntity(JdbcAuthorizationSchema entity) {
        return mapper.map(entity, AuthorizationSchema.class);
    }

    protected JdbcAuthorizationSchema toJdbcEntity(AuthorizationSchema entity) {
        return mapper.map(entity, JdbcAuthorizationSchema.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement("authorization_schemas", columns);
        this.updateStatement = createUpdateStatement("authorization_schemas", columns, List.of(COL_ID));
    }

    @Override
    public Maybe<AuthorizationSchema> findById(String id) {
        LOGGER.debug("findById({})", id);
        return this.authorizationSchemaRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthorizationSchema> findByDomain(String domainId) {
        LOGGER.debug("findByDomain({})", domainId);
        return this.authorizationSchemaRepository.findByDomain(domainId)
                .map(this::toEntity);
    }

    @Override
    public Single<AuthorizationSchema> create(AuthorizationSchema item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create authorizationSchema with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertStatement);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DOMAIN_ID, item.getDomainId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_ENGINE_TYPE, item.getEngineType(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CONTENT, item.getContent(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_VERSION, item.getVersion(), Integer.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<AuthorizationSchema> update(AuthorizationSchema item) {
        LOGGER.debug("Update authorizationSchema with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec updateSpec = getTemplate().getDatabaseClient().sql(updateStatement);

        updateSpec = addQuotedField(updateSpec, COL_ID, item.getId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_DOMAIN_ID, item.getDomainId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_ENGINE_TYPE, item.getEngineType(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_CONTENT, item.getContent(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_VERSION, item.getVersion(), Integer.class);
        updateSpec = addQuotedField(updateSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateSpec = addQuotedField(updateSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = updateSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.authorizationSchemaRepository.deleteById(id);
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("deleteByDomain({})", domainId);
        return monoToCompletable(getTemplate().delete(JdbcAuthorizationSchema.class)
                .matching(Query.query(where("domain_id").is(domainId)))
                .all())
                .observeOn(Schedulers.computation());
    }
}
