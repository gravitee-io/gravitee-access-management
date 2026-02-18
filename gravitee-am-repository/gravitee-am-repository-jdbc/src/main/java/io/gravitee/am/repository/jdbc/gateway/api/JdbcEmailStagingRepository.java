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
package io.gravitee.am.repository.jdbc.gateway.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.gravitee.am.repository.gateway.api.EmailStagingRepository;
import io.gravitee.am.repository.jdbc.gateway.api.model.JdbcEmailStaging;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.provider.common.DateHelper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcEmailStagingRepository extends AbstractJdbcRepository implements EmailStagingRepository {

    @Override
    public Single<EmailStaging> create(EmailStaging emailStaging) {
        emailStaging.setId(emailStaging.getId() == null ? RandomString.generate() : emailStaging.getId());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        emailStaging.setCreatedAt(DateHelper.toDate(now));
        emailStaging.setUpdatedAt(DateHelper.toDate(now));

        LOGGER.debug("Create EmailStaging with id {}", emailStaging.getId());

        JdbcEmailStaging jdbcEntity = toJdbcEntity(emailStaging);
        return monoToSingle(getTemplate().insert(jdbcEntity))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("Delete EmailStaging with id '{}'", id);

        return monoToCompletable(
                getTemplate().delete(JdbcEmailStaging.class)
                        .matching(Query.query(where("id").is(id)))
                        .all()
        ).observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Completable.complete();
        }

        LOGGER.debug("Delete EmailStaging with ids '{}'", ids);

        return monoToCompletable(
                getTemplate().delete(JdbcEmailStaging.class)
                        .matching(Query.query(where("id").in(ids)))
                        .all()
        ).observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<EmailStaging> findOldestByUpdateDate(Reference reference, int limit) {
        LOGGER.debug("Find oldest EmailStaging entries by update date with limit {}", limit);

        Query query = Query.query(where(REFERENCE_ID_FIELD).is(reference.id())
                        .and(where(REF_TYPE_FIELD).is(reference.type().name())))
                .sort(Sort.by("updated_at").ascending())
                .limit(limit);

        return fluxToFlowable(getTemplate().select(query, JdbcEmailStaging.class))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<EmailStaging> updateAttempts(String id, int attempts) {
        LOGGER.debug("Update EmailStaging attempts for id '{}' to {}", id, attempts);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Update update = Update.update("attempts", attempts)
                .set("updated_at", now);

        return monoToSingle(
                getTemplate().update(JdbcEmailStaging.class)
                        .matching(Query.query(where("id").is(id)))
                        .apply(update)
                        .then(getTemplate().select(Query.query(where("id").is(id)), JdbcEmailStaging.class).singleOrEmpty())
        )
        .map(this::toEntity)
        .observeOn(Schedulers.computation());
    }

    protected EmailStaging toEntity(JdbcEmailStaging jdbcEntity) {
        if (jdbcEntity == null) {
            return null;
        }

        EmailStaging emailStaging = new EmailStaging();
        emailStaging.setId(jdbcEntity.getId());
        emailStaging.setUserId(jdbcEntity.getUserId());
        emailStaging.setApplicationId(jdbcEntity.getApplicationId());
        emailStaging.setReferenceType(jdbcEntity.getReferenceType() != null ?
                io.gravitee.am.model.ReferenceType.valueOf(jdbcEntity.getReferenceType()) : null);
        emailStaging.setReferenceId(jdbcEntity.getReferenceId());
        emailStaging.setEmailTemplateName(jdbcEntity.getEmailTemplateName());
        emailStaging.setAttempts(jdbcEntity.getAttempts());
        emailStaging.setCreatedAt(DateHelper.toDate(jdbcEntity.getCreatedAt()));
        emailStaging.setUpdatedAt(DateHelper.toDate(jdbcEntity.getUpdatedAt()));
        return emailStaging;
    }

    protected JdbcEmailStaging toJdbcEntity(EmailStaging emailStaging) {
        if (emailStaging == null) {
            return null;
        }

        JdbcEmailStaging jdbcEntity = new JdbcEmailStaging();
        jdbcEntity.setId(emailStaging.getId());
        jdbcEntity.setUserId(emailStaging.getUserId());
        jdbcEntity.setApplicationId(emailStaging.getApplicationId());
        jdbcEntity.setReferenceType(emailStaging.getReferenceType() != null ?
                emailStaging.getReferenceType().name() : null);
        jdbcEntity.setReferenceId(emailStaging.getReferenceId());
        jdbcEntity.setEmailTemplateName(emailStaging.getEmailTemplateName());
        jdbcEntity.setAttempts(emailStaging.getAttempts());
        jdbcEntity.setCreatedAt(DateHelper.toLocalDateTime(emailStaging.getCreatedAt()));
        jdbcEntity.setUpdatedAt(DateHelper.toLocalDateTime(emailStaging.getUpdatedAt()));
        return jdbcEntity;
    }
}
