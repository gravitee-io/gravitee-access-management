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
import io.gravitee.am.model.AuthorizationSchemaVersion;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthorizationSchemaVersion;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringAuthorizationSchemaVersionRepository;
import io.gravitee.am.repository.management.api.AuthorizationSchemaVersionRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
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
public class JdbcAuthorizationSchemaVersionRepository extends AbstractJdbcRepository implements AuthorizationSchemaVersionRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_SCHEMA_ID = "schema_id";
    public static final String COL_VERSION = "version";
    public static final String COL_CONTENT = "content";
    public static final String COL_COMMIT_MESSAGE = "commit_message";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_CREATED_BY = "created_by";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_SCHEMA_ID,
            COL_VERSION,
            COL_CONTENT,
            COL_COMMIT_MESSAGE,
            COL_CREATED_AT,
            COL_CREATED_BY
    );

    private String insertStatement;
    private String updateStatement;

    @Autowired
    private SpringAuthorizationSchemaVersionRepository authorizationSchemaVersionRepository;

    protected AuthorizationSchemaVersion toEntity(JdbcAuthorizationSchemaVersion entity) {
        return mapper.map(entity, AuthorizationSchemaVersion.class);
    }

    protected JdbcAuthorizationSchemaVersion toJdbcEntity(AuthorizationSchemaVersion entity) {
        return mapper.map(entity, JdbcAuthorizationSchemaVersion.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement("authorization_schema_versions", columns);
        this.updateStatement = createUpdateStatement("authorization_schema_versions", columns, List.of(COL_ID));
    }

    @Override
    public Maybe<AuthorizationSchemaVersion> findById(String id) {
        LOGGER.debug("findById({})", id);
        return this.authorizationSchemaVersionRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Flowable<AuthorizationSchemaVersion> findBySchemaId(String schemaId) {
        LOGGER.debug("findBySchemaId({})", schemaId);
        return this.authorizationSchemaVersionRepository.findBySchemaId(schemaId)
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthorizationSchemaVersion> findBySchemaIdAndVersion(String schemaId, int version) {
        LOGGER.debug("findBySchemaIdAndVersion({}, {})", schemaId, version);
        return this.authorizationSchemaVersionRepository.findBySchemaIdAndVersion(schemaId, version)
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthorizationSchemaVersion> findLatestBySchemaId(String schemaId) {
        LOGGER.debug("findLatestBySchemaId({})", schemaId);
        return this.authorizationSchemaVersionRepository.findLatestBySchemaId(schemaId)
                .map(this::toEntity);
    }

    @Override
    public Single<AuthorizationSchemaVersion> create(AuthorizationSchemaVersion item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create authorizationSchemaVersion with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertStatement);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_SCHEMA_ID, item.getSchemaId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_VERSION, item.getVersion(), Integer.class);
        insertSpec = addQuotedField(insertSpec, COL_CONTENT, item.getContent(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_COMMIT_MESSAGE, item.getCommitMessage(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_BY, item.getCreatedBy(), String.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<AuthorizationSchemaVersion> update(AuthorizationSchemaVersion item) {
        LOGGER.debug("Update authorizationSchemaVersion with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec updateSpec = getTemplate().getDatabaseClient().sql(updateStatement);

        updateSpec = addQuotedField(updateSpec, COL_ID, item.getId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_SCHEMA_ID, item.getSchemaId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_VERSION, item.getVersion(), Integer.class);
        updateSpec = addQuotedField(updateSpec, COL_CONTENT, item.getContent(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_COMMIT_MESSAGE, item.getCommitMessage(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateSpec = addQuotedField(updateSpec, COL_CREATED_BY, item.getCreatedBy(), String.class);

        Mono<Long> action = updateSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.authorizationSchemaVersionRepository.deleteById(id);
    }

    @Override
    public Completable deleteBySchemaId(String schemaId) {
        LOGGER.debug("deleteBySchemaId({})", schemaId);
        return monoToCompletable(getTemplate().delete(JdbcAuthorizationSchemaVersion.class)
                .matching(Query.query(where("schema_id").is(schemaId)))
                .all());
    }
}
