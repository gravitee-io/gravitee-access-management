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
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthorizationDetailType;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringAuthorizationDetailTypeRepository;
import io.gravitee.am.repository.exceptions.UniqueConstraintViolationException;
import io.gravitee.am.repository.management.api.AuthorizationDetailTypeRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAuthorizationDetailTypeRepository extends AbstractJdbcRepository implements AuthorizationDetailTypeRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_DOMAIN = "domain";
    public static final String COL_TYPE = "type";
    public static final String COL_NAME = "name";
    public static final String COL_DESCRIPTION = "description";
    // reserved on MySQL, MariaDB and SQL Server: quote it in every statement
    public static final String COL_SCHEMA = "schema";
    public static final String COL_DISCOVERY = "discovery";
    public static final String COL_EXPIRES_IN = "expires_in";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_DOMAIN,
            COL_TYPE,
            COL_NAME,
            COL_DESCRIPTION,
            COL_SCHEMA,
            COL_DISCOVERY,
            COL_EXPIRES_IN,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    private static final String FROM_DOMAIN = " FROM " + JdbcAuthorizationDetailType.TABLE_NAME + " WHERE " + COL_DOMAIN + " = :domain";
    private static final String ORDER_BY_TYPE = "UPPER(" + COL_TYPE + ")";
    private static final char LIKE_ESCAPE = '!';

    private String insertStatement;
    private String updateStatement;

    @Autowired
    private SpringAuthorizationDetailTypeRepository authorizationDetailTypeRepository;

    protected AuthorizationDetailType toEntity(JdbcAuthorizationDetailType entity) {
        return mapper.map(entity, AuthorizationDetailType.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement(JdbcAuthorizationDetailType.TABLE_NAME, columns);
        this.updateStatement = createUpdateStatement(JdbcAuthorizationDetailType.TABLE_NAME, columns, List.of(COL_ID));
    }

    @Override
    public Maybe<AuthorizationDetailType> findById(String id) {
        LOGGER.debug("findById({})", id);
        return authorizationDetailTypeRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Flowable<AuthorizationDetailType> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return authorizationDetailTypeRepository.findByDomain(domain)
                .map(this::toEntity);
    }

    @Override
    public Single<Page<AuthorizationDetailType>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("findByDomain({}, {}, {})", domain, page, size);
        return findPage(FROM_DOMAIN, Map.of("domain", domain), page, size);
    }

    @Override
    public Single<Page<AuthorizationDetailType>> search(String domain, String query, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {})", domain, query, page, size);
        String upperQuery = query.toUpperCase(Locale.ROOT);
        String condition = query.contains("*")
                ? " AND UPPER(" + COL_TYPE + ") LIKE :value ESCAPE '" + LIKE_ESCAPE + "'"
                : " AND UPPER(" + COL_TYPE + ") = :value";
        String value = query.contains("*")
                ? escapeLike(upperQuery).replaceAll("\\*+", "%")
                : upperQuery;
        return findPage(FROM_DOMAIN + condition, Map.of("domain", domain, "value", value), page, size);
    }

    /**
     * Escapes the LIKE special characters of every supported database, including SQL Server's {@code [}.
     */
    private static String escapeLike(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_' || c == '[') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private Single<Page<AuthorizationDetailType>> findPage(String fromWhere, Map<String, String> parameters, int page, int size) {
        DatabaseClient.GenericExecuteSpec select = getTemplate().getDatabaseClient()
                .sql("SELECT *" + fromWhere + databaseDialectHelper.buildPagingClause(ORDER_BY_TYPE, true, page, size));
        DatabaseClient.GenericExecuteSpec count = getTemplate().getDatabaseClient().sql("SELECT COUNT(*)" + fromWhere);
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            select = select.bind(parameter.getKey(), parameter.getValue());
            count = count.bind(parameter.getKey(), parameter.getValue());
        }
        Single<List<AuthorizationDetailType>> data = fluxToFlowable(select
                        .map((row, rowMetadata) -> rowMapper.read(JdbcAuthorizationDetailType.class, row))
                        .all())
                .map(this::toEntity)
                .toList();
        Single<Long> total = monoToSingle(count.map((row, rowMetadata) -> row.get(0, Long.class)).first());
        return Single.zip(data, total, (types, totalCount) -> new Page<>(types, page, totalCount))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationDetailType> findByDomainAndId(String domain, String id) {
        LOGGER.debug("findByDomainAndId({}, {})", domain, id);
        return authorizationDetailTypeRepository.findByDomainAndId(domain, id)
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthorizationDetailType> findByDomainAndType(String domain, String type) {
        LOGGER.debug("findByDomainAndType({}, {})", domain, type);
        return authorizationDetailTypeRepository.findByDomainAndType(domain, type)
                .firstElement()
                .map(this::toEntity);
    }

    @Override
    public Single<AuthorizationDetailType> create(AuthorizationDetailType item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create authorizationDetailType with id {}", item.getId());
        DatabaseClient.GenericExecuteSpec insertSpec = bind(getTemplate().getDatabaseClient().sql(insertStatement), item);
        return monoToSingle(insertSpec.fetch().rowsUpdated())
                .onErrorResumeNext(ex -> Single.error(ex instanceof DuplicateKeyException
                        ? new UniqueConstraintViolationException("Authorization detail type " + item.getType() + " already exists in domain " + item.getDomain(), ex)
                        : ex))
                .flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<AuthorizationDetailType> update(AuthorizationDetailType item) {
        LOGGER.debug("Update authorizationDetailType with id {}", item.getId());
        DatabaseClient.GenericExecuteSpec updateSpec = bind(getTemplate().getDatabaseClient().sql(updateStatement), item);
        return monoToSingle(updateSpec.fetch().rowsUpdated()).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(getTemplate().delete(JdbcAuthorizationDetailType.class)
                .matching(Query.query(where(COL_ID).is(id)))
                .all());
    }

    @Override
    public Completable deleteByDomain(String domain) {
        LOGGER.debug("deleteByDomain({})", domain);
        return monoToCompletable(getTemplate().delete(JdbcAuthorizationDetailType.class)
                .matching(Query.query(where(COL_DOMAIN).is(domain)))
                .all())
                .observeOn(Schedulers.computation());
    }

    private DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, AuthorizationDetailType item) {
        spec = addQuotedField(spec, COL_ID, item.getId(), String.class);
        spec = addQuotedField(spec, COL_DOMAIN, item.getDomain(), String.class);
        spec = addQuotedField(spec, COL_TYPE, item.getType(), String.class);
        spec = addQuotedField(spec, COL_NAME, item.getName(), String.class);
        spec = addQuotedField(spec, COL_DESCRIPTION, item.getDescription(), String.class);
        spec = addQuotedField(spec, COL_SCHEMA, item.getSchema(), String.class);
        spec = addQuotedField(spec, COL_DISCOVERY, item.isDiscovery(), Boolean.class);
        spec = addQuotedField(spec, COL_EXPIRES_IN, item.getExpiresIn(), Integer.class);
        spec = addQuotedField(spec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        return addQuotedField(spec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
    }
}
