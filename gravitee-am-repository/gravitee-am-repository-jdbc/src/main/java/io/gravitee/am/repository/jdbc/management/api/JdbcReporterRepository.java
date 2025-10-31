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
import io.gravitee.am.model.Reporter;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcReporter;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringReporterRepository;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.LocalDateTime;
import java.util.List;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
@RequiredArgsConstructor
public class JdbcReporterRepository extends AbstractJdbcRepository implements ReporterRepository, InitializingBean {

    public static final String COL_ID = "id";

    protected final SpringReporterRepository reporterRepository;


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
                .inherited(entity.isInherited())
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
                .inherited(entity.isInherited())
                .build();

    }

    private static final List<FieldSpec<JdbcReporter, ?>> fields = List.of(
            new FieldSpec<>(COL_ID, JdbcReporter::getId, String.class),
            new FieldSpec<>("reference_type", r-> r.getReferenceType().name(), String.class),
            new FieldSpec<>("reference_id", JdbcReporter::getReferenceId, String.class),
            new FieldSpec<>("enabled", JdbcReporter::isEnabled, boolean.class),
            new FieldSpec<>("type", JdbcReporter::getType, String.class),
            new FieldSpec<>("name", JdbcReporter::getName, String.class),
            new FieldSpec<>("data_type", JdbcReporter::getDataType, String.class),
            new FieldSpec<>("configuration", JdbcReporter::getConfiguration, String.class),
            new FieldSpec<>("system", JdbcReporter::isSystem, boolean.class),
            new FieldSpec<>("inherited", JdbcReporter::isInherited, boolean.class),
            new FieldSpec<>("created_at", JdbcReporter::getCreatedAt, LocalDateTime.class),
            new FieldSpec<>("updated_at", JdbcReporter::getUpdatedAt, LocalDateTime.class)

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
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Reporter> findByReference(Reference reference) {
        LOGGER.debug("findByReference({})", reference);
        return reporterRepository.findByReferenceTypeAndReferenceId(reference.type(), reference.id())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Reporter> findById(String id) {
        LOGGER.debug("findById({})", id);
        return reporterRepository.findById(id)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Reporter> findInheritedFrom(Reference parentReference) {
        return reporterRepository.findByReferenceTypeAndReferenceIdAndInheritedTrue(parentReference.type(), parentReference.id())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Reporter> create(Reporter item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Reporter with id {}", item.getId());
        return createOrUpdate(item, getTemplate().getDatabaseClient().sql(insertStatement))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Reporter> update(Reporter item) {
        LOGGER.debug("Update reporter with id '{}'", item.getId());
        return createOrUpdate(item, getTemplate().getDatabaseClient().sql(updateStatement))
                .observeOn(Schedulers.computation());
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
        return reporterRepository.deleteById(id)
                .observeOn(Schedulers.computation());
    }

}
