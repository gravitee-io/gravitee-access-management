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
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthorizationEngine;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringAuthorizationEngineRepository;
import io.gravitee.am.repository.management.api.AuthorizationEngineRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAuthorizationEngineRepository extends AbstractJdbcRepository implements AuthorizationEngineRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_TYPE = "type";
    public static final String COL_CONFIGURATION = "configuration";
    public static final String COL_REFERENCE_TYPE = "reference_type";
    public static final String COL_REFERENCE_ID = "reference_id";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_NAME,
            COL_TYPE,
            COL_CONFIGURATION,
            COL_REFERENCE_TYPE,
            COL_REFERENCE_ID,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    private String insertStatement;
    private String updateStatement;

    @Autowired
    private SpringAuthorizationEngineRepository authorizationEngineRepository;

    protected AuthorizationEngine toEntity(JdbcAuthorizationEngine entity) {
        return mapper.map(entity, AuthorizationEngine.class);
    }

    protected JdbcAuthorizationEngine toJdbcEntity(AuthorizationEngine entity) {
        return mapper.map(entity, JdbcAuthorizationEngine.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement("authorization_engines", columns);
        this.updateStatement = createUpdateStatement("authorization_engines", columns, List.of(COL_ID));
    }

    @Override
    public Maybe<AuthorizationEngine> findById(String id) {
        LOGGER.debug("findById({})", id);
        return this.authorizationEngineRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Flowable<AuthorizationEngine> findAll() {
        LOGGER.debug("findAll()");
        return this.authorizationEngineRepository.findAll().map(this::toEntity);
    }

    @Override
    public Flowable<AuthorizationEngine> findByDomain(String domain) {
        LOGGER.debug("findAll({}, {})", ReferenceType.DOMAIN.name(), domain);
        return this.authorizationEngineRepository.findAll(ReferenceType.DOMAIN.name(), domain)
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthorizationEngine> findByDomainAndId(String domainId, String id) {
        LOGGER.debug("findByDomainAndId({}, {})", domainId, id);
        return this.authorizationEngineRepository.findById(ReferenceType.DOMAIN.name(), domainId, id)
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthorizationEngine> findByDomainAndType(String domainId, String type) {
        LOGGER.debug("findByDomainAndType({}, {})", domainId, type);
        return this.authorizationEngineRepository.findByType(ReferenceType.DOMAIN.name(), domainId, type)
                .map(this::toEntity);
    }

    @Override
    public Single<AuthorizationEngine> create(AuthorizationEngine item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create authorizationEngine with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertStatement);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_NAME, item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TYPE, item.getType(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CONFIGURATION, item.getConfiguration(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<AuthorizationEngine> update(AuthorizationEngine item) {
        LOGGER.debug("Update authorizationEngine with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec updateSpec = getTemplate().getDatabaseClient().sql(updateStatement);

        updateSpec = addQuotedField(updateSpec, COL_ID, item.getId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_NAME, item.getName(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_TYPE, item.getType(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_CONFIGURATION, item.getConfiguration(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateSpec = addQuotedField(updateSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = updateSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.authorizationEngineRepository.deleteById(id);
    }
}
