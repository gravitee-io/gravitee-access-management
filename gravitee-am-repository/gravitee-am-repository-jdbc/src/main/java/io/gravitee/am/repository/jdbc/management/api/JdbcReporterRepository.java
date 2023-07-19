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
import io.gravitee.am.model.Reporter;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcReporter;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringReporterRepository;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcReporterRepository extends AbstractJdbcRepository implements ReporterRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_DOMAIN = "domain";
    public static final String COL_ENABLED = "enabled";
    public static final String COL_TYPE = "type";
    public static final String COL_NAME = "name";
    public static final String COL_DATA_TYPE = "data_type";
    public static final String COL_CONFIG = "configuration";
    public static final String COL_IS_SYSTEM = "system";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    @Autowired
    protected SpringReporterRepository reporterRepository;

    protected Reporter toEntity(JdbcReporter entity) {
        return mapper.map(entity, Reporter.class);
    }

    protected JdbcReporter toJdbcEntity(Reporter entity) {
        return mapper.map(entity, JdbcReporter.class);
    }
    private static final List<String> columns = List.of(COL_ID,
            COL_DOMAIN,
            COL_ENABLED,
            COL_TYPE,
            COL_NAME,
            COL_DATA_TYPE,
            COL_CONFIG,
            COL_IS_SYSTEM,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );
    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("reporters", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("reporters", columns, List.of(COL_ID));
    }

    @Override
    public Flowable<Reporter> findAll() {
        LOGGER.debug("findAll()");
        return reporterRepository.findAll()
                .map(this::toEntity);
    }

    @Override
    public Flowable<Reporter> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return reporterRepository.findByDomain(domain)
                .map(this::toEntity);
    }

    @Override
    public Maybe<Reporter> findById(String id) {
        LOGGER.debug("findById({})", id);
        return reporterRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<Reporter> create(Reporter item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Reporter with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DOMAIN, item.getDomain(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_ENABLED, item.isEnabled(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TYPE, item.getType(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_NAME, item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DATA_TYPE, item.getDataType(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CONFIG, item.getConfiguration(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_IS_SYSTEM, item.isSystem(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();
        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<Reporter> update(Reporter item) {
        LOGGER.debug("Update reporter with id '{}'", item.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec updateSpec = getTemplate().getDatabaseClient().sql(UPDATE_STATEMENT);

        updateSpec = addQuotedField(updateSpec, COL_ID, item.getId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_DOMAIN, item.getDomain(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_ENABLED, item.isEnabled(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_TYPE, item.getType(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_NAME, item.getName(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_DATA_TYPE, item.getDataType(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_CONFIG, item.getConfiguration(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_IS_SYSTEM, item.isSystem(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateSpec = addQuotedField(updateSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = updateSpec.fetch().rowsUpdated();
        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return reporterRepository.deleteById(id);
    }

}
