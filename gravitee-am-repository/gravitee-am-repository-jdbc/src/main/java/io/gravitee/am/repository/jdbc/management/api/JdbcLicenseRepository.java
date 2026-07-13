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

import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcLicense;
import io.gravitee.am.repository.management.api.LicenseRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class JdbcLicenseRepository extends AbstractJdbcRepository implements LicenseRepository, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcLicenseRepository.class);

    private static final String TABLE = "licenses";
    private static final String COL_REFERENCE_ID = "reference_id";
    private static final String COL_REFERENCE_TYPE = "reference_type";
    private static final String COL_LICENSE = "license";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> COLUMNS = List.of(
            COL_REFERENCE_ID,
            COL_REFERENCE_TYPE,
            COL_LICENSE,
            COL_CREATED_AT,
            COL_UPDATED_AT);

    private static final List<String> KEY_COLUMNS = List.of(COL_REFERENCE_ID, COL_REFERENCE_TYPE);

    private String insertStatement;
    private String updateStatement;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement(TABLE, COLUMNS);
        // only the mutable columns are updated, the composite key is used in the WHERE clause
        this.updateStatement = createUpdateStatement(TABLE, List.of(COL_LICENSE, COL_UPDATED_AT), KEY_COLUMNS);
    }

    @Override
    public Flowable<License> findAll() {
        LOGGER.debug("findAll()");
        return findAll(Query.empty(), JdbcLicense.class)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<License> findById(String referenceId, ReferenceType referenceType) {
        LOGGER.debug("findById({}, {})", referenceId, referenceType);
        return findOne(Query.query(where(COL_REFERENCE_ID).is(referenceId)
                        .and(where(COL_REFERENCE_TYPE).is(referenceType.name()))), JdbcLicense.class)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<License> create(License item) {
        LOGGER.debug("create license for {} {}", item.getReferenceType(), item.getReferenceId());

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertStatement);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_LICENSE, item.getLicense(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();
        return monoToSingle(action)
                .flatMap(i -> findById(item.getReferenceId(), item.getReferenceType()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<License> update(License item) {
        LOGGER.debug("update license for {} {}", item.getReferenceType(), item.getReferenceId());

        DatabaseClient.GenericExecuteSpec update = getTemplate().getDatabaseClient().sql(updateStatement);
        update = addQuotedField(update, COL_LICENSE, item.getLicense(), String.class);
        update = addQuotedField(update, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        update = addQuotedField(update, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);

        Mono<Long> action = update.fetch().rowsUpdated();
        return monoToSingle(action)
                .flatMap(i -> findById(item.getReferenceId(), item.getReferenceType()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String referenceId, ReferenceType referenceType) {
        LOGGER.debug("delete license for {} {}", referenceType, referenceId);
        Mono<Long> delete = getTemplate().delete(JdbcLicense.class)
                .matching(Query.query(where(COL_REFERENCE_ID).is(referenceId)
                        .and(where(COL_REFERENCE_TYPE).is(referenceType.name()))))
                .all();
        return monoToCompletable(delete)
                .observeOn(Schedulers.computation());
    }

    private License toEntity(JdbcLicense entity) {
        if (entity == null) {
            return null;
        }
        License license = new License();
        license.setReferenceId(entity.getReferenceId());
        license.setReferenceType(entity.getReferenceType() == null ? null : ReferenceType.valueOf(entity.getReferenceType()));
        license.setLicense(entity.getLicense());
        license.setCreatedAt(dateConverter.convertFrom(entity.getCreatedAt(), null));
        license.setUpdatedAt(dateConverter.convertFrom(entity.getUpdatedAt(), null));
        return license;
    }
}
