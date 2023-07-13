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

import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcI18nDictionary;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringI18nDictionaryRepository;
import io.gravitee.am.repository.management.api.I18nDictionaryRepository;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static io.gravitee.am.common.utils.RandomString.generate;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

@Repository
public class JdbcI18nDictionaryRepository extends AbstractJdbcRepository implements I18nDictionaryRepository, InitializingBean {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String LOCALE = "locale";
    private static final String REFERENCE_ID = "reference_id";
    private static final String REFERENCE_TYPE = "reference_type";
    private static final String CREATED_AT = "created_at";
    private static final String UPDATED_AT = "updated_at";

    private static final String ENTRY_ID = "dictionary_id";
    private static final String ENTRY_KEY = "key";
    private static final String ENTRY_MESSAGE = "message";

    private String insertStatement;
    private String insertEntryStatement;
    private String updateStatement;
    @Autowired
    private SpringI18nDictionaryRepository repository;

    private static final List<String> columns = List.of(
            ID,
            REFERENCE_TYPE,
            REFERENCE_ID,
            NAME,
            LOCALE,
            CREATED_AT,
            UPDATED_AT
    );

    private static final List<String> entryColumns = List.of(
            ENTRY_ID,
            ENTRY_KEY,
            ENTRY_MESSAGE
    );

    @Override
    public void afterPropertiesSet() throws Exception {
        insertStatement = createInsertStatement("i18n_dictionaries", columns);
        insertEntryStatement = createInsertStatement("i18n_dictionary_entries", entryColumns);
        updateStatement = createUpdateStatement("i18n_dictionaries", columns, List.of(ID));
    }

    @Override
    public Single<I18nDictionary> create(I18nDictionary item) {
        item.setId(item.getId() == null ? generate() : item.getId());
        LOGGER.debug("Create i18n dictionary with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec sql = template.getDatabaseClient().sql(insertStatement);
        sql = addQuotedField(sql, ID, item.getId(), String.class);
        sql = addQuotedField(sql, NAME, item.getName(), String.class);
        sql = addQuotedField(sql, LOCALE, item.getLocale(), String.class);
        sql = addQuotedField(sql, REFERENCE_ID, item.getReferenceId(), String.class);
        sql = addQuotedField(sql, REFERENCE_TYPE, item.getReferenceType().toString(), String.class);
        sql = addQuotedField(sql, CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        sql = addQuotedField(sql, UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        Mono<Long> insertAction = sql.fetch().rowsUpdated();

        return monoToSingle(insertAction.as(mono -> TransactionalOperator.create(tm).transactional(mono)))
                .flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<I18nDictionary> update(I18nDictionary item) {
        LOGGER.debug("Update i18n dictionary with id {}", item.getId());


        TransactionalOperator trx = TransactionalOperator.create(tm);
        DatabaseClient.GenericExecuteSpec sql = template.getDatabaseClient().sql(updateStatement);
        sql = addQuotedField(sql, ID, item.getId(), String.class);
        sql = addQuotedField(sql, NAME, item.getName(), String.class);
        sql = addQuotedField(sql, LOCALE, item.getLocale(), String.class);
        sql = addQuotedField(sql, REFERENCE_ID, item.getReferenceId(), String.class);
        sql = addQuotedField(sql, REFERENCE_TYPE, item.getReferenceType().toString(), String.class);
        sql = addQuotedField(sql, CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        sql = addQuotedField(sql, UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<? extends Number> updateAction = sql.fetch().rowsUpdated();
        updateAction = updateAction.then(deleteEntries(item.getId()));
        updateAction = persistEntries(updateAction, item);

        return monoToSingle(updateAction.as(trx::transactional)).flatMap(i -> this.findById(item.getId()).toSingle())
                .doOnError(error -> LOGGER.error("unable to update i18n dictionary with id {}", item.getId(), error));
    }

    private Mono<Integer> deleteEntries(String dictionaryId) {
        return template.delete(JdbcI18nDictionary.Entry.class).matching(Query.query(where(ENTRY_ID).is(dictionaryId))).all();
    }

    private Mono<? extends Number> persistEntries(Mono<? extends Number> insertAction, I18nDictionary item) {
        Mono<? extends Number> entries;
        if (!item.getEntries().isEmpty()) {
            List<JdbcI18nDictionary.Entry> jdbcEntries = new ArrayList<>();
            item.getEntries().forEach((key, message) -> {
                var jdbcEntry = new JdbcI18nDictionary.Entry();
                jdbcEntry.setDictionaryId(item.getId());
                jdbcEntry.setKey(key);
                jdbcEntry.setMessage(message);
                jdbcEntries.add(jdbcEntry);
            });
            entries = Flux.fromIterable(jdbcEntries).concatMap(entry -> {
                DatabaseClient.GenericExecuteSpec insert = template.getDatabaseClient().sql(insertEntryStatement);
                insert = insert.bind(ENTRY_ID, entry.getDictionaryId());
                insert = insert.bind(ENTRY_KEY, entry.getKey());
                insert = insert.bind(ENTRY_MESSAGE, entry.getMessage());
                return insert.fetch().rowsUpdated();
            }).reduce(Long::sum);
            insertAction = insertAction.then(entries);
        }
        return insertAction;
    }


    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete i18n dictionary with id {}", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        return monoToCompletable(template.delete(JdbcI18nDictionary.class)
                                         .matching(Query.query(where(ID).is(id)))
                                         .all()
                                         .then(deleteEntries(id))
                                         .as(trx::transactional));
    }

    @Override
    public Maybe<I18nDictionary> findByLocale(ReferenceType referenceType, String referenceId, String locale) {
        LOGGER.debug("findByLocale({}, {}, {})", referenceType, referenceId, locale);
        return repository.findByLocale(referenceType.toString(), referenceId, locale)
                         .map(this::toEntity)
                         .flatMapSingle(this::completeDictionary);
    }

    @Override
    public Flowable<I18nDictionary> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findAll({}, {})", referenceType, referenceId);
        return repository.findAll(referenceType.toString(), referenceId)
                         .map(this::toEntity)
                         .flatMap(dictionary -> completeDictionary(dictionary).toFlowable());
    }

    @Override
    public Maybe<I18nDictionary> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("findById({}, {}, {})", referenceType, referenceId, id);
        return repository.findById(referenceType.toString(), referenceId, id)
                         .map(this::toEntity)
                         .flatMapSingle(this::completeDictionary);
    }

    @Override
    public Maybe<I18nDictionary> findById(String id) {
        LOGGER.debug("findById({})", id);
        return repository.findById(id)
                         .map(this::toEntity)
                         .flatMapSingle(this::completeDictionary);
    }

    private Single<I18nDictionary> completeDictionary(I18nDictionary dictionary) {
        return fluxToFlowable(template.select(JdbcI18nDictionary.Entry.class)
                                      .matching(Query.query(where(ENTRY_ID).is(dictionary.getId())))
                                      .all()).toList().map(entries -> {
            if (!entries.isEmpty()) {
                entries.forEach(entry -> dictionary.getEntries().put(entry.getKey(), entry.getMessage()));
            }
            return dictionary;
        });
    }

    protected I18nDictionary toEntity(JdbcI18nDictionary entity) {
        return mapper.map(entity, I18nDictionary.class);
    }
}
