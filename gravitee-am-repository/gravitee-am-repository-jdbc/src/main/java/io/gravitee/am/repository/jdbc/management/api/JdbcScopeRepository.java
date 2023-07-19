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
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcScope;
import io.gravitee.am.repository.jdbc.management.api.spring.scope.SpringScopeClaimRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.scope.SpringScopeRepository;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcScopeRepository extends AbstractJdbcRepository implements ScopeRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_DOMAIN = "domain";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_EXPIRES_IN = "expires_in";
    public static final String COL_ICON_URI = "icon_uri";
    public static final String COL_KEY = "key";
    public static final String COL_DISCOVERY = "discovery";
    public static final String COL_PARAMETERIZED = "parameterized";
    public static final String COL_SYSTEM = "system";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_NAME,
            COL_DOMAIN,
            COL_DESCRIPTION,
            COL_EXPIRES_IN,
            COL_ICON_URI,
            COL_KEY,
            COL_DISCOVERY,
            COL_PARAMETERIZED,
            COL_SYSTEM,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

    @Autowired
    private SpringScopeRepository scopeRepository;

    @Autowired
    private SpringScopeClaimRepository claimRepository;

    protected Scope toEntity(JdbcScope entity) {
        return mapper.map(entity, Scope.class);
    }

    protected JdbcScope toJdbcEntity(Scope entity) {
        return mapper.map(entity, JdbcScope.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("scopes", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("scopes", columns, List.of(COL_ID));
    }

    @Override
    public Single<Page<Scope>> findByDomain(String domain, int page, int size) {

        LOGGER.debug("findByDomain({}, {}, {})", domain, page, size);
        return fluxToFlowable(getTemplate().select(Query.query(from(where(COL_DOMAIN).is(domain)))
                                .sort(Sort.by(databaseDialectHelper.toSql(SqlIdentifier.quoted(COL_KEY))))
                                .with(PageRequest.of(page, size)), JdbcScope.class))
                .map(this::toEntity)
                .flatMap(scope -> completeWithClaims(Maybe.just(scope), scope.getId()).toFlowable())
                .toList()
                .flatMap(content -> countByDomain(domain)
                        .map((count) -> new Page<>(content, page, count)));
    }

    private Single<Long> countByDomain(String domain) {
        return monoToSingle(getTemplate().getDatabaseClient().sql("select count(s."+databaseDialectHelper.toSql(SqlIdentifier.quoted(COL_KEY))+") from scopes s where s.domain = :domain")
                .bind(COL_DOMAIN, domain)
                .map((row, rowMetadata) -> row.get(0, Long.class))
                .first());
    }

    @Override
    public Single<Page<Scope>> search(String domain, String query, int page, int size) {
        LOGGER.debug("search({}, {})", domain, query);

        boolean wildcardSearch = query.contains("*");
        String wildcardQuery = query.replaceAll("\\*+", "%");

        String search = this.databaseDialectHelper.buildSearchScopeQuery(wildcardSearch, page, size);
        String count = this.databaseDialectHelper.buildCountScopeQuery(wildcardSearch);

        return fluxToFlowable(getTemplate().getDatabaseClient().sql(search)
                .bind(COL_DOMAIN, domain)
                .bind("value", wildcardSearch ? wildcardQuery.toUpperCase() : query.toUpperCase())
                .map((row, rowMetadata) -> rowMapper.read(JdbcScope.class, row))
                .all())
                .map(this::toEntity)
                .flatMap(scope -> completeWithClaims(Maybe.just(scope), scope.getId()).toFlowable())
                .toList()
                .flatMap(data -> monoToSingle(getTemplate().getDatabaseClient().sql(count)
                        .bind(COL_DOMAIN, domain)
                        .bind("value", wildcardSearch ? wildcardQuery.toUpperCase() : query.toUpperCase())
                        .map((row, rowMetadata) -> row.get(0, Long.class))
                        .first())
                        .map(total -> new Page<>(data, page, total)));
    }

    private Maybe<Scope> completeWithClaims(Maybe<Scope> maybeScope, String id) {
        Maybe<List<String>> scopeClaims = claimRepository.findByScopeId(id)
                .map(JdbcScope.Claims::getClaim)
                .toList()
                .toMaybe();

        return maybeScope.zipWith(scopeClaims, (scope, claims) -> {
            LOGGER.debug("findById({}) fetch {} scopeClaims", id, claims == null ? 0 : claims.size());
            scope.setClaims(claims);
            return scope;
        });
    }

    @Override
    public Maybe<Scope> findByDomainAndKey(String domain, String key) {
        LOGGER.debug("findByDomainAndKey({}, {})", domain, key);
        return monoToMaybe(getTemplate().select(Query.query(where(COL_DOMAIN).is(domain)
                        .and(where(databaseDialectHelper.toSql(SqlIdentifier.quoted(COL_KEY))).is(key)))
                .limit(1), JdbcScope.class).singleOrEmpty()
        ).map(this::toEntity).flatMap(scope -> completeWithClaims(Maybe.just(scope), scope.getId()));
    }

    @Override
    public Flowable<Scope> findByDomainAndKeys(String domain, List<String> keys) {
        LOGGER.debug("findByDomainAndKeys({}, {})", domain, keys);
        return fluxToFlowable(getTemplate().select(
                Query.query(where(COL_DOMAIN).is(domain)
                        .and(where(databaseDialectHelper.toSql(SqlIdentifier.quoted(COL_KEY))).in(keys))), JdbcScope.class))
                .map(this::toEntity)
                .flatMap(scope -> completeWithClaims(Maybe.just(scope), scope.getId()).toFlowable());
    }

    @Override
    public Maybe<Scope> findById(String id) {
        LOGGER.debug("findById({})", id);
        return scopeRepository.findById(id)
                .map(this::toEntity)
                .flatMap(scope -> completeWithClaims(Maybe.just(scope), scope.getId()));
    }

    @Override
    public Single<Scope> create(Scope item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Scope with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_NAME, item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DOMAIN, item.getDomain(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DESCRIPTION, item.getDescription(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_EXPIRES_IN, item.getExpiresIn(), Integer.class);
        insertSpec = addQuotedField(insertSpec, COL_ICON_URI, item.getIconUri(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_KEY, item.getKey(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DISCOVERY, item.isDiscovery(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, COL_PARAMETERIZED, item.isParameterized(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, COL_SYSTEM, item.isSystem(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        final List<String> scopeClaims = item.getClaims();
        if (scopeClaims != null && !scopeClaims.isEmpty()) {
            action = action.then(Flux.fromIterable(scopeClaims).concatMap(claim -> insertClaim(claim, item)).reduce(Long::sum));
        }

        return monoToSingle(action.as(trx::transactional)).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    private Mono<Long> insertClaim(String claim, Scope item) {
        return getTemplate().getDatabaseClient().sql("INSERT INTO scope_claims(scope_id, claim) VALUES(:scope_id, :claim)")
                .bind("scope_id", item.getId())
                .bind("claim", claim)
                .fetch().rowsUpdated();
    }

    @Override
    public Single<Scope> update(Scope item) {
        LOGGER.debug("Update Scope with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> deleteClaims = getTemplate().delete(JdbcScope.Claims.class)
                .matching(Query.query(where("scope_id").is(item.getId()))).all();

        DatabaseClient.GenericExecuteSpec update = getTemplate().getDatabaseClient().sql(UPDATE_STATEMENT);

        update = addQuotedField(update, COL_ID, item.getId(), String.class);
        update = addQuotedField(update, COL_NAME, item.getName(), String.class);
        update = addQuotedField(update, COL_DOMAIN, item.getDomain(), String.class);
        update = addQuotedField(update, COL_DESCRIPTION, item.getDescription(), String.class);
        update = addQuotedField(update, COL_EXPIRES_IN, item.getExpiresIn(), Integer.class);
        update = addQuotedField(update, COL_ICON_URI, item.getIconUri(), String.class);
        update = addQuotedField(update, COL_KEY, item.getKey(), String.class);
        update = addQuotedField(update, COL_DISCOVERY, item.isDiscovery(), Boolean.class);
        update = addQuotedField(update, COL_PARAMETERIZED, item.isParameterized(), Boolean.class);
        update = addQuotedField(update, COL_SYSTEM, item.isSystem(), Boolean.class);
        update = addQuotedField(update, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = update.fetch().rowsUpdated();

        final List<String> scopeClaims = item.getClaims();
        if (scopeClaims != null && !scopeClaims.isEmpty()) {
            action = action.then(Flux.fromIterable(scopeClaims).concatMap(claim -> insertClaim(claim, item)).reduce(Long::sum));
        }

        return monoToSingle(deleteClaims.then(action).as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> deleteClaim = getTemplate().delete(JdbcScope.Claims.class)
                .matching(Query.query(where("scope_id").is(id))).all();

        Mono<Integer> delete = getTemplate().delete(JdbcScope.class)
                .matching(Query.query(where(COL_ID).is(id))).all();

        return monoToCompletable(deleteClaim.then(delete).as(trx::transactional));
    }

}
