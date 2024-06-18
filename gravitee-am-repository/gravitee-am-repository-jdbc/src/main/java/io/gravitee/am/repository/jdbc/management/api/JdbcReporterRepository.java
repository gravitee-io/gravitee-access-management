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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;

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
    public static final String COL_REFERENCE_TYPE = "reference_type";
    public static final String COL_REFERENCE_ID = "reference_id";
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
        return Reporter.builder()
                .id(entity.getId())
                .reference(new Reference(entity.getReferenceType(), entity.getReferenceId()))
                .name(entity.getName())
                .configuration(entity.getConfiguration())
                .enabled(entity.isEnabled())
                .system(entity.isSystem())
                .dataType(entity.getDataType())
                .createdAt(toDate(entity.getCreatedAt()))
                .updatedAt(toDate(entity.getUpdatedAt()))
                .type(entity.getType())
                .build();
    }

    protected JdbcReporter toJdbcEntity(Reporter entity) {
        return JdbcReporter.builder()
                .id(entity.getId())
                .referenceType(entity.getReference().type())
                .referenceId(entity.getReference().id())
                .name(entity.getName())
                .configuration(entity.getConfiguration())
                .enabled(entity.isEnabled())
                .system(entity.isSystem())
                .dataType(entity.getDataType())
                .createdAt(toLocalDateTime(entity.getCreatedAt()))
                .updatedAt(toLocalDateTime(entity.getUpdatedAt()))
                .type(entity.getType())
                .build();

    }

    private static final List<FieldSpec<JdbcReporter, ?>> fields = List.of(
            new FieldSpec<>(COL_ID, JdbcReporter::getId, String.class),
            new FieldSpec<>(COL_REFERENCE_TYPE, JdbcReporter::getReferenceType, ReferenceType.class),
            new FieldSpec<>(COL_REFERENCE_ID, JdbcReporter::getReferenceId, String.class),
            new FieldSpec<>(COL_ENABLED, JdbcReporter::isEnabled, boolean.class),
            new FieldSpec<>(COL_TYPE, JdbcReporter::getType, String.class),
            new FieldSpec<>(COL_NAME, JdbcReporter::getName, String.class),
            new FieldSpec<>(COL_DATA_TYPE, JdbcReporter::getDataType, String.class),
            new FieldSpec<>(COL_CONFIG, JdbcReporter::getConfiguration, String.class),
            new FieldSpec<>(COL_IS_SYSTEM, JdbcReporter::isSystem, boolean.class),
            new FieldSpec<>(COL_CREATED_AT, JdbcReporter::getCreatedAt, LocalDateTime.class),
            new FieldSpec<>(COL_UPDATED_AT, JdbcReporter::getUpdatedAt, LocalDateTime.class)

    );
    private String insertStatement;
    private String updateStatement;

    @Override
    public void afterPropertiesSet() throws Exception {
        var columns = fields.stream().map(FieldSpec::columnName).toList();
        this.insertStatement = createInsertStatement("reporters", columns);
        this.updateStatement = createUpdateStatement("reporters", columns, List.of(COL_ID));
    }

    @Override
    public Flowable<Reporter> findAll() {
        LOGGER.debug("findAll()");
        return reporterRepository.findAll()
                .map(this::toEntity);
    }

    @Override
    public Flowable<Reporter> findByReference(Reference reference) {
        LOGGER.debug("findByDomain({})", reference);
        return reporterRepository.findByReferenceTypeAndReferenceId(reference.type(), reference.id())
                .map(this::toEntity);
    }

    @Override
    public Maybe<Reporter> findById(String id) {
        LOGGER.debug("findById({})", id);
        return reporterRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    @Transactional
    public Single<Reporter> create(Reporter item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Reporter with id {}", item.getId());
        return createOrUpdate(item, getTemplate().getDatabaseClient().sql(insertStatement));
    }

    @Override
    public Single<Reporter> update(Reporter item) {
        LOGGER.debug("Update reporter with id '{}'", item.getId());
        return createOrUpdate(item, getTemplate().getDatabaseClient().sql(updateStatement));
    }

    Single<Reporter> createOrUpdate(Reporter item, DatabaseClient.GenericExecuteSpec spec) {
        var effectiveSpec = addQuotedFields(spec, fields, toJdbcEntity(item));
        TransactionalOperator trx = TransactionalOperator.create(tm);
        return monoToSingle(effectiveSpec.fetch()
                .rowsUpdated()
                .as(trx::transactional))
                .flatMapMaybe(x -> this.findById(item.getId()))
                .toSingle();
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return reporterRepository.deleteById(id);
    }

}
