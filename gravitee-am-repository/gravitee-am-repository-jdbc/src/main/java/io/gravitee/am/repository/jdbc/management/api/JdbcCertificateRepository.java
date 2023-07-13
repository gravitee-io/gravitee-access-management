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
import io.gravitee.am.model.Certificate;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcCertificate;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringCertificateRepository;
import io.gravitee.am.repository.management.api.CertificateRepository;
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
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcCertificateRepository extends AbstractJdbcRepository implements CertificateRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_CONFIGURATION = "configuration";
    public static final String COL_DOMAIN = "domain";
    public static final String COL_NAME = "name";
    public static final String COL_METADATA = "metadata";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";
    public static final String COL_EXPIRES_AT = "expires_at";
    public static final String COL_SYSTEM = "system";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_TYPE,
            COL_CONFIGURATION,
            COL_DOMAIN,
            COL_NAME,
            COL_METADATA,
            COL_CREATED_AT,
            COL_UPDATED_AT,
            COL_EXPIRES_AT,
            COL_SYSTEM
    );

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

    @Autowired
    private SpringCertificateRepository certificateRepository;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("certificates", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("certificates", columns, List.of(COL_ID));
    }

    protected Certificate toEntity(JdbcCertificate entity) {
        Certificate cert = mapper.map(entity, Certificate.class);
        if (cert != null && cert.getMetadata() != null && cert.getMetadata().containsKey("file")) {
            Object file = cert.getMetadata().get("file");
            if (file instanceof String) {
                // file value should be Byte[] but Jackson serialize it in B64
                byte[] data = Base64.getDecoder().decode((String) file);
                cert.getMetadata().put("file", data);
            }
        }
        return cert;
    }

    protected JdbcCertificate toJdbcEntity(Certificate entity) {
        return mapper.map(entity, JdbcCertificate.class);
    }

    @Override
    public Flowable<Certificate> findAll() {
        LOGGER.debug("findAll()");
        return this.certificateRepository.findAll()
                .map(this::toEntity);
    }

    @Override
    public Flowable<Certificate> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return this.certificateRepository.findByDomain(domain)
                .map(this::toEntity);
    }

    @Override
    public Maybe<Certificate> findById(String id) {
        LOGGER.debug("findById({})", id);
        return this.certificateRepository.findById(id)
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve Certificate with id {}", id, error));
    }

    @Override
    public Single<Certificate> create(Certificate item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create certificate with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = template.getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec,COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec,COL_TYPE, item.getType(), String.class);
        insertSpec = addQuotedField(insertSpec,COL_CONFIGURATION, item.getConfiguration(), String.class);
        insertSpec = addQuotedField(insertSpec,COL_DOMAIN, item.getDomain(), String.class);
        insertSpec = addQuotedField(insertSpec,COL_NAME, item.getName(), String.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, COL_METADATA, item.getMetadata());
        insertSpec = addQuotedField(insertSpec,COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,COL_EXPIRES_AT, dateConverter.convertTo(item.getExpiresAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,COL_SYSTEM, item.isSystem(), boolean.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create certificate with id {}", item.getId(), error));
    }

    @Override
    public Single<Certificate> update(Certificate item) {
        LOGGER.debug("update Certificate with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec update = template.getDatabaseClient().sql(UPDATE_STATEMENT);

        update = addQuotedField(update,COL_ID, item.getId(), String.class);
        update = addQuotedField(update,COL_TYPE, item.getType(), String.class);
        update = addQuotedField(update,COL_CONFIGURATION, item.getConfiguration(), String.class);
        update = addQuotedField(update,COL_DOMAIN, item.getDomain(), String.class);
        update = addQuotedField(update,COL_NAME, item.getName(), String.class);
        update = databaseDialectHelper.addJsonField(update, COL_METADATA, item.getMetadata());
        update = addQuotedField(update,COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update,COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update,COL_EXPIRES_AT, dateConverter.convertTo(item.getExpiresAt(), null), LocalDateTime.class);
        update = addQuotedField(update,COL_SYSTEM, item.isSystem(), boolean.class);

        Mono<Long> updateAction = update.fetch().rowsUpdated();

        return monoToSingle(updateAction).flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to update certificate with id {}", item.getId(), error));
    }

    @Override
    public Completable updateExpirationDate(String certificateId, Date expiresAt) {
        LOGGER.debug("update Certificate expiration date with id {} with value '{}'", certificateId, expiresAt);

        DatabaseClient.GenericExecuteSpec update = template.getDatabaseClient().sql("UPDATE certificates SET " + COL_EXPIRES_AT + " = :" + COL_EXPIRES_AT + " WHERE " + COL_ID +" = :"+COL_ID);
        update = addQuotedField(update,COL_ID, certificateId, String.class);
        update = addQuotedField(update, COL_EXPIRES_AT, dateConverter.convertTo(expiresAt, null), LocalDateTime.class);

        return monoToCompletable(update.fetch().rowsUpdated());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.certificateRepository.deleteById(id)
                .doOnError(error -> LOGGER.error("Unable to delete Certificate with id {}", id, error));
    }
}
