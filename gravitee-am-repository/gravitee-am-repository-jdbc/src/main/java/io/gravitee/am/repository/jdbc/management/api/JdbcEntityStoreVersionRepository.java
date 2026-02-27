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
import io.gravitee.am.model.EntityStoreVersion;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcEntityStoreVersion;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringEntityStoreVersionRepository;
import io.gravitee.am.repository.management.api.EntityStoreVersionRepository;
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
public class JdbcEntityStoreVersionRepository extends AbstractJdbcRepository implements EntityStoreVersionRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_ENTITY_STORE_ID = "entity_store_id";
    public static final String COL_VERSION = "version";
    public static final String COL_CONTENT = "content";
    public static final String COL_COMMIT_MESSAGE = "commit_message";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_CREATED_BY = "created_by";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_ENTITY_STORE_ID,
            COL_VERSION,
            COL_CONTENT,
            COL_COMMIT_MESSAGE,
            COL_CREATED_AT,
            COL_CREATED_BY
    );

    private String insertStatement;
    private String updateStatement;

    @Autowired
    private SpringEntityStoreVersionRepository entityStoreVersionRepository;

    protected EntityStoreVersion toEntity(JdbcEntityStoreVersion entity) {
        return mapper.map(entity, EntityStoreVersion.class);
    }

    protected JdbcEntityStoreVersion toJdbcEntity(EntityStoreVersion entity) {
        return mapper.map(entity, JdbcEntityStoreVersion.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement("entity_store_versions", columns);
        this.updateStatement = createUpdateStatement("entity_store_versions", columns, List.of(COL_ID));
    }

    @Override
    public Maybe<EntityStoreVersion> findById(String id) {
        LOGGER.debug("findById({})", id);
        return this.entityStoreVersionRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Flowable<EntityStoreVersion> findByEntityStoreId(String entityStoreId) {
        LOGGER.debug("findByEntityStoreId({})", entityStoreId);
        return this.entityStoreVersionRepository.findByEntityStoreId(entityStoreId)
                .map(this::toEntity);
    }

    @Override
    public Maybe<EntityStoreVersion> findByEntityStoreIdAndVersion(String entityStoreId, int version) {
        LOGGER.debug("findByEntityStoreIdAndVersion({}, {})", entityStoreId, version);
        return this.entityStoreVersionRepository.findByEntityStoreIdAndVersion(entityStoreId, version)
                .map(this::toEntity);
    }

    @Override
    public Maybe<EntityStoreVersion> findLatestByEntityStoreId(String entityStoreId) {
        LOGGER.debug("findLatestByEntityStoreId({})", entityStoreId);
        return this.entityStoreVersionRepository.findLatestByEntityStoreId(entityStoreId)
                .map(this::toEntity);
    }

    @Override
    public Single<EntityStoreVersion> create(EntityStoreVersion item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create entityStoreVersion with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertStatement);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_ENTITY_STORE_ID, item.getEntityStoreId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_VERSION, item.getVersion(), Integer.class);
        insertSpec = addQuotedField(insertSpec, COL_CONTENT, item.getContent(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_COMMIT_MESSAGE, item.getCommitMessage(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_BY, item.getCreatedBy(), String.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<EntityStoreVersion> update(EntityStoreVersion item) {
        LOGGER.debug("Update entityStoreVersion with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec updateSpec = getTemplate().getDatabaseClient().sql(updateStatement);

        updateSpec = addQuotedField(updateSpec, COL_ID, item.getId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_ENTITY_STORE_ID, item.getEntityStoreId(), String.class);
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
        return this.entityStoreVersionRepository.deleteById(id);
    }

    @Override
    public Completable deleteByEntityStoreId(String entityStoreId) {
        LOGGER.debug("deleteByEntityStoreId({})", entityStoreId);
        return monoToCompletable(getTemplate().delete(JdbcEntityStoreVersion.class)
                .matching(Query.query(where("entity_store_id").is(entityStoreId)))
                .all());
    }
}
