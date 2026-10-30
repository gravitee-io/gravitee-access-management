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
package io.gravitee.am.repository.jdbc.common;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.common.model.AbstractJdbcEmailStaging;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.provider.common.DateHelper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

public abstract class AbstractEmailStagingRepository<T extends AbstractJdbcEmailStaging> extends AbstractJdbcRepository {

    private final Class<T> jdbcEntityClass;

    private final Supplier<T> jdbcEntityFactory;

    private final String tableName;

    protected AbstractEmailStagingRepository(Class<T> jdbcEntityClass, Supplier<T> jdbcEntityFactory, String tableName) {
        this.jdbcEntityClass = jdbcEntityClass;
        this.jdbcEntityFactory = jdbcEntityFactory;
        this.tableName = tableName;
    }

    public Single<EmailStaging> create(EmailStaging emailStaging) {
        emailStaging.setId(emailStaging.getId() == null ? RandomString.generate() : emailStaging.getId());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        emailStaging.setCreatedAt(DateHelper.toDate(now));
        emailStaging.setUpdatedAt(DateHelper.toDate(now));

        LOGGER.debug("Create EmailStaging with id {}", emailStaging.getId());

        return monoToSingle(getTemplate().insert(toJdbcEntity(emailStaging)))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    public Completable delete(String id) {
        LOGGER.debug("Delete EmailStaging with id '{}'", id);

        return monoToCompletable(
                getTemplate().delete(jdbcEntityClass)
                        .matching(Query.query(where("id").is(id)))
                        .all()
        ).observeOn(Schedulers.computation());
    }

    public Completable delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Completable.complete();
        }

        LOGGER.debug("Delete EmailStaging with ids '{}'", ids);

        return monoToCompletable(
                getTemplate().delete(jdbcEntityClass)
                        .matching(Query.query(where("id").in(ids)))
                        .all()
        ).observeOn(Schedulers.computation());
    }

    public Flowable<EmailStaging> findOldestByUpdateDate(Reference reference, int limit) {
        LOGGER.debug("Find oldest EmailStaging entries by update date with limit {}", limit);

        Query query = Query.query(where(REFERENCE_ID_FIELD).is(reference.id())
                        .and(where(REF_TYPE_FIELD).is(reference.type().name())))
                .sort(Sort.by("updated_at").ascending())
                .limit(limit);

        return fluxToFlowable(getTemplate().select(query, jdbcEntityClass))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    public Single<EmailStaging> updateAttempts(String id, int attempts) {
        LOGGER.debug("Update EmailStaging attempts for id '{}' to {}", id, attempts);

        Update update = Update.update("attempts", attempts)
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC));

        return monoToSingle(
                getTemplate().update(jdbcEntityClass)
                        .matching(Query.query(where("id").is(id)))
                        .apply(update)
                        .then(getTemplate().select(Query.query(where("id").is(id)), jdbcEntityClass).singleOrEmpty())
        )
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    public Flowable<Reference> listReferences() {
        LOGGER.debug("List distinct References in EmailStaging");

        return fluxToFlowable(
                getTemplate().getDatabaseClient()
                        .sql("SELECT DISTINCT reference_type, reference_id FROM " + tableName)
                        .map((row, rowMetadata) -> new Reference(
                                ReferenceType.valueOf(row.get("reference_type", String.class)),
                                row.get("reference_id", String.class)
                        ))
                        .all()
        ).observeOn(Schedulers.computation());
    }

    protected EmailStaging toEntity(T jdbcEntity) {
        if (jdbcEntity == null) {
            return null;
        }

        EmailStaging emailStaging = new EmailStaging();
        emailStaging.setId(jdbcEntity.getId());
        emailStaging.setUserId(jdbcEntity.getUserId());
        emailStaging.setApplicationId(jdbcEntity.getApplicationId());
        emailStaging.setReferenceType(jdbcEntity.getReferenceType() != null ? ReferenceType.valueOf(jdbcEntity.getReferenceType()) : null);
        emailStaging.setReferenceId(jdbcEntity.getReferenceId());
        emailStaging.setEmailTemplateName(jdbcEntity.getEmailTemplateName());
        emailStaging.setAttempts(jdbcEntity.getAttempts());
        emailStaging.setCreatedAt(DateHelper.toDate(jdbcEntity.getCreatedAt()));
        emailStaging.setUpdatedAt(DateHelper.toDate(jdbcEntity.getUpdatedAt()));
        return emailStaging;
    }

    protected T toJdbcEntity(EmailStaging emailStaging) {
        if (emailStaging == null) {
            return null;
        }

        T jdbcEntity = jdbcEntityFactory.get();
        jdbcEntity.setId(emailStaging.getId());
        jdbcEntity.setUserId(emailStaging.getUserId());
        jdbcEntity.setApplicationId(emailStaging.getApplicationId());
        jdbcEntity.setReferenceType(emailStaging.getReferenceType() != null ? emailStaging.getReferenceType().name() : null);
        jdbcEntity.setReferenceId(emailStaging.getReferenceId());
        jdbcEntity.setEmailTemplateName(emailStaging.getEmailTemplateName());
        jdbcEntity.setAttempts(emailStaging.getAttempts());
        jdbcEntity.setCreatedAt(DateHelper.toLocalDateTime(emailStaging.getCreatedAt()));
        jdbcEntity.setUpdatedAt(DateHelper.toLocalDateTime(emailStaging.getUpdatedAt()));
        return jdbcEntity;
    }
}
