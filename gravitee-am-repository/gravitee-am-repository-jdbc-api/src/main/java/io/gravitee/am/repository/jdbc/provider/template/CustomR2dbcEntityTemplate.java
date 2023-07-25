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
package io.gravitee.am.repository.jdbc.provider.template;

import java.util.Map;
import java.util.Optional;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.core.StatementMapper;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.CriteriaDefinition;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.util.ProxyUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.Parameter;
import org.springframework.r2dbc.core.PreparedOperation;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomR2dbcEntityTemplate extends R2dbcEntityTemplate {


    public CustomR2dbcEntityTemplate(DatabaseClient databaseClient, ReactiveDataAccessStrategy strategy) {
        super(databaseClient, strategy);
    }

    @Override
    public Mono<Integer> delete(Query query, Class<?> entityClass) throws DataAccessException {
        Assert.notNull(query, "Query must not be null");
        Assert.notNull(entityClass, "Entity class must not be null");

        return doDelete(query, entityClass, getTableName(entityClass));
    }

    Mono<Integer> doDelete(Query query, Class<?> entityClass, SqlIdentifier tableName) {
        StatementMapper statementMapper = getDataAccessStrategy().getStatementMapper().forType(entityClass);

        StatementMapper.DeleteSpec deleteSpec = statementMapper //
                .createDelete(tableName);

        Optional<CriteriaDefinition> criteria = query.getCriteria();
        if (criteria.isPresent()) {
            deleteSpec = criteria.map(deleteSpec::withCriteria).orElse(deleteSpec);
        }

        PreparedOperation<?> operation = statementMapper.getMappedObject(deleteSpec);
        return getDatabaseClient().sql(operation).fetch().rowsUpdated().map(Long::intValue).defaultIfEmpty(0);
    }

    @Override
    public <T> Mono<T> update(T entity) throws DataAccessException {
        Assert.notNull(entity, "Entity must not be null");

        return doUpdate(entity, getRequiredEntity(entity).getTableName());
    }

    private <T> Mono<T> doUpdate(T entity, SqlIdentifier tableName) {
        RelationalPersistentEntity<T> persistentEntity = getRequiredEntity(entity);
        return maybeCallBeforeConvert(entity, tableName).flatMap(onBeforeConvert -> {

            T entityToUse;
            Criteria matchingVersionCriteria;

            if (persistentEntity.hasVersionProperty()) {
                matchingVersionCriteria = createMatchingVersionCriteria(onBeforeConvert, persistentEntity);
                entityToUse = incrementVersion(persistentEntity, onBeforeConvert);
            } else {
                entityToUse = onBeforeConvert;
                matchingVersionCriteria = null;
            }
            OutboundRow outboundRow = getDataAccessStrategy().getOutboundRow(entityToUse);
            return maybeCallBeforeSave(entityToUse, outboundRow, tableName) //
                    .flatMap(onBeforeSave -> {
                        SqlIdentifier idColumn = persistentEntity.getRequiredIdProperty().getColumnName();
                        Parameter id = outboundRow.remove(idColumn);
                        Criteria criteria = Criteria.where(getDataAccessStrategy().toSql(idColumn)).is(id);
                        if (matchingVersionCriteria != null) {
                            criteria = criteria.and(matchingVersionCriteria);
                        }
                        return doUpdate(onBeforeSave, tableName, persistentEntity, criteria, outboundRow);
                    });
        });
    }

    private <T> Mono<T> doUpdate(T entity, SqlIdentifier tableName, RelationalPersistentEntity<T> persistentEntity,
                                 Criteria criteria, OutboundRow outboundRow) {

        Update update = Update.from((Map) outboundRow);

        StatementMapper mapper = getDataAccessStrategy().getStatementMapper();
        StatementMapper.UpdateSpec updateSpec = mapper.createUpdate(tableName, update).withCriteria(criteria);

        PreparedOperation<?> operation = mapper.getMappedObject(updateSpec);

        return getDatabaseClient().sql(operation)
                .fetch()
                .rowsUpdated()
                .handle((rowsUpdated, sink) -> {

                    if (rowsUpdated != 0) {
                        return;
                    }

                    if (persistentEntity.hasVersionProperty()) {
                        sink.error(new OptimisticLockingFailureException(
                                formatOptimisticLockingExceptionMessage(entity, persistentEntity)));
                    } else {
                        sink.error(new TransientDataAccessResourceException(
                                formatTransientEntityExceptionMessage(entity, persistentEntity)));
                    }
                }).then(maybeCallAfterSave(entity, outboundRow, tableName));
    }

    private <T> String formatOptimisticLockingExceptionMessage(T entity, RelationalPersistentEntity<T> persistentEntity) {

        return String.format("Failed to update table [%s]. Version does not match for row with Id [%s].",
                persistentEntity.getTableName(), persistentEntity.getIdentifierAccessor(entity).getIdentifier());
    }

    private <T> String formatTransientEntityExceptionMessage(T entity, RelationalPersistentEntity<T> persistentEntity) {

        return String.format("Failed to update table [%s]. Row with Id [%s] does not exist.",
                persistentEntity.getTableName(), persistentEntity.getIdentifierAccessor(entity).getIdentifier());
    }
    private <T> T incrementVersion(RelationalPersistentEntity<T> persistentEntity, T entity) {

        PersistentPropertyAccessor<?> propertyAccessor = persistentEntity.getPropertyAccessor(entity);
        RelationalPersistentProperty versionProperty = persistentEntity.getVersionProperty();

        ConversionService conversionService = getDataAccessStrategy().getConverter().getConversionService();
        Object currentVersionValue = propertyAccessor.getProperty(versionProperty);
        long newVersionValue = 1L;
        if (currentVersionValue != null) {
            newVersionValue = conversionService.convert(currentVersionValue, Long.class) + 1;
        }
        Class<?> versionPropertyType = versionProperty.getType();
        propertyAccessor.setProperty(versionProperty, conversionService.convert(newVersionValue, versionPropertyType));

        return (T) propertyAccessor.getBean();
    }

    private <T> Criteria createMatchingVersionCriteria(T entity, RelationalPersistentEntity<T> persistentEntity) {

        PersistentPropertyAccessor<?> propertyAccessor = persistentEntity.getPropertyAccessor(entity);
        RelationalPersistentProperty versionProperty = persistentEntity.getVersionProperty();

        Object version = propertyAccessor.getProperty(versionProperty);
        Criteria.CriteriaStep versionColumn = Criteria.where(getDataAccessStrategy().toSql(versionProperty.getColumnName()));
        if (version == null) {
            return versionColumn.isNull();
        } else {
            return versionColumn.is(version);
        }
    }


    public Mono<Integer> update(Query query, Update update, Class<?> entityClass) throws DataAccessException {
        Assert.notNull(query, "Query must not be null");
        Assert.notNull(update, "Update must not be null");
        Assert.notNull(entityClass, "Entity class must not be null");
        return this.doUpdate(query, update, entityClass, this.getTableName(entityClass)).map(Long::intValue);
    }

    private SqlIdentifier getTableName(Class<?> entityClass) {
        return this.getRequiredEntity(entityClass).getTableName();
    }

    private <T> RelationalPersistentEntity<T> getRequiredEntity(T entity) {
        Class<?> entityType = ProxyUtils.getUserClass(entity);
        return (RelationalPersistentEntity) getRequiredEntity(entityType);
    }

    private RelationalPersistentEntity<?> getRequiredEntity(Class<?> entityClass) {
        return (RelationalPersistentEntity) getDataAccessStrategy().getConverter().getMappingContext().getRequiredPersistentEntity(entityClass);
    }

    private Mono<Long> doUpdate(Query query, Update update, Class<?> entityClass, SqlIdentifier tableName) {
        StatementMapper statementMapper = getDataAccessStrategy().getStatementMapper().forType(entityClass);
        StatementMapper.UpdateSpec selectSpec = statementMapper.createUpdate(tableName, update);
        Optional<CriteriaDefinition> criteria = query.getCriteria();
        if (criteria.isPresent()) {
            selectSpec.getClass();
            selectSpec = (StatementMapper.UpdateSpec)criteria.map(selectSpec::withCriteria).orElse(selectSpec);
        }

        PreparedOperation<?> operation = statementMapper.getMappedObject(selectSpec);
        return getDatabaseClient().sql(operation).fetch().rowsUpdated();
    }
}
