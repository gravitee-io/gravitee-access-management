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

import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcPasswordHistory;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringPasswordHistoryRepository;
import io.gravitee.am.repository.management.api.PasswordHistoryRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static io.gravitee.am.common.utils.RandomString.generate;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

@Repository
public class JdbcPasswordHistoryRepository extends AbstractJdbcRepository implements PasswordHistoryRepository, InitializingBean {

    private static final String ID = "id";
    private static final String REFERENCE_ID = "reference_id";
    private static final String REFERENCE_TYPE = "reference_type";
    private static final String CREATED_AT = "created_at";
    private static final String UPDATED_AT = "updated_at";

    private static final String USER_ID = "user_id";
    private static final String PASSWORD = "password";

    private String insertStatement;
    private String updateStatement;
    @Autowired
    private SpringPasswordHistoryRepository repository;

    private static final List<String> columns = List.of(
            ID,
            REFERENCE_TYPE,
            REFERENCE_ID,
            CREATED_AT,
            UPDATED_AT,
            USER_ID,
            PASSWORD
    );

    @Override
    public void afterPropertiesSet() throws Exception {
        insertStatement = createInsertStatement("password_histories", columns);
        updateStatement = createUpdateStatement("password_histories", columns, List.of(ID));
    }

    @Override
    public Single<PasswordHistory> create(PasswordHistory item) {
        item.setId(item.getId() == null ? generate() : item.getId());
        LOGGER.debug("Create password history with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec sql = getTemplate().getDatabaseClient().sql(insertStatement);
        sql = addQuotedField(sql, ID, item.getId(), String.class);
        sql = addQuotedField(sql, REFERENCE_ID, item.getReferenceId(), String.class);
        sql = addQuotedField(sql, REFERENCE_TYPE, item.getReferenceType().toString(), String.class);
        sql = addQuotedField(sql, CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        sql = addQuotedField(sql, UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        sql = addQuotedField(sql, USER_ID, item.getUserId(), String.class);
        sql = addQuotedField(sql, PASSWORD, item.getPassword(), String.class);

        Mono<Long> insertAction = sql.fetch().rowsUpdated();

        return monoToSingle(insertAction.as(mono -> TransactionalOperator.create(tm).transactional(mono)))
                .flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<PasswordHistory> update(PasswordHistory item) {
        LOGGER.debug("Update password history with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        DatabaseClient.GenericExecuteSpec sql = getTemplate().getDatabaseClient().sql(updateStatement);
        sql = addQuotedField(sql, ID, item.getId(), String.class);
        sql = addQuotedField(sql, REFERENCE_ID, item.getReferenceId(), String.class);
        sql = addQuotedField(sql, REFERENCE_TYPE, item.getReferenceType().toString(), String.class);
        sql = addQuotedField(sql, CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        sql = addQuotedField(sql, UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        sql = addQuotedField(sql, USER_ID, item.getUserId(), String.class);
        sql = addQuotedField(sql, PASSWORD, item.getPassword(), String.class);

        Mono<Long> updateAction = sql.fetch().rowsUpdated();

        return monoToSingle(updateAction.as(trx::transactional)).flatMap(i -> this.findById(item.getId()).toSingle())
                .doOnError(error -> LOGGER.error("unable to update password history with id {}", item.getId(), error));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete password history with id {}", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        return monoToCompletable(getTemplate().delete(JdbcPasswordHistory.class)
                                         .matching(Query.query(where(ID).is(id)))
                                         .all()
                                         .as(trx::transactional));
    }

    @Override
    public Maybe<PasswordHistory> findById(String id) {
        LOGGER.debug("findById({})", id);
        return repository.findById(id)
                         .map(this::toEntity);
    }

    protected PasswordHistory toEntity(JdbcPasswordHistory entity) {
        return mapper.map(entity, PasswordHistory.class);
    }

    @Override
    public Flowable<PasswordHistory> findByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({}, {})", referenceType, referenceId);
        return repository.findByReference(referenceType.toString(), referenceId).map(this::toEntity);

    }

    @Override
    public Flowable<PasswordHistory> findUserHistory(ReferenceType referenceType, String referenceId, String userId) {
        LOGGER.debug("findByUserId({})", userId);
        return repository.findByUserId(referenceType.toString(), referenceId, userId).map(this::toEntity);
    }

    @Override
    public Completable deleteByUserId(String userId) {
        LOGGER.debug("deleteByUserId({})", userId);
        return monoToCompletable(this.getTemplate().delete(JdbcPasswordHistory.class).matching(
                query(where(USER_ID).is(userId))).all());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("deleteByReference({})", referenceId);
        return monoToCompletable(this.getTemplate().delete(JdbcPasswordHistory.class).matching(
                query(where(REFERENCE_TYPE).is(referenceType)
                                           .and(where(REFERENCE_ID).is(referenceId)))).all());
    }
}
