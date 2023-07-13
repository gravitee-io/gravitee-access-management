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
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcApplication;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcApplication.Identity;
import io.gravitee.am.repository.jdbc.management.api.spring.application.SpringApplicationFactorRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.application.SpringApplicationIdentityRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.application.SpringApplicationRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.application.SpringApplicationScopeRepository;
import io.gravitee.am.repository.management.api.ApplicationRepository;
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
import java.util.*;

import static java.util.stream.Collectors.toCollection;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcApplicationRepository extends AbstractJdbcRepository implements ApplicationRepository, InitializingBean {

    public static final int MAX_CONCURRENCY = 1;

    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_ENABLED = "enabled";
    public static final String COL_TEMPLATE = "template";
    public static final String COL_NAME = "name";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_DOMAIN = "domain";
    public static final String COL_CERTIFICATE = "certificate";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";
    public static final String COL_METADATA = "metadata";
    public static final String COL_SETTINGS = "settings";

    private static final List<String> columns = List.of(COL_ID,
            COL_TYPE,
            COL_ENABLED,
            COL_TEMPLATE,
            COL_NAME,
            COL_DESCRIPTION,
            COL_DOMAIN,
            COL_CERTIFICATE,
            COL_CREATED_AT,
            COL_UPDATED_AT,
            COL_METADATA,
            COL_SETTINGS);

    @Autowired
    private SpringApplicationRepository applicationRepository;

    @Autowired
    private SpringApplicationFactorRepository factorRepository;

    @Autowired
    private SpringApplicationScopeRepository scopeRepository;

    @Autowired
    private SpringApplicationIdentityRepository identityRepository;

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

    protected Application toEntity(JdbcApplication entity) {
        return mapper.map(entity, Application.class);
    }

    protected JdbcApplication toJdbcEntity(Application entity) {
        return mapper.map(entity, JdbcApplication.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("applications", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("applications", columns, List.of(COL_ID));
    }

    private Single<Application> completeApplication(Application entity) {
        return Single.just(entity).flatMap(app ->
                identityRepository.findAllByApplicationId(app.getId()).toList()
                        .map(idps -> idps.stream().map(this::convertIdentity).collect(toCollection(TreeSet::new)))
                        .map(identities -> {
                            app.setIdentityProviders(identities);
                            return app;
                        })
        ).flatMap(app ->
                factorRepository.findAllByApplicationId(app.getId()).map(JdbcApplication.Factor::getFactor).toList().map(factors -> {
                    app.setFactors(new HashSet<>(factors));
                    return app;
                })
        ).flatMap(app ->
                scopeRepository.findAllByApplicationId(app.getId()).map(jdbcScopeSettings -> mapper.map(jdbcScopeSettings, ApplicationScopeSettings.class)).toList().map(scopeSettings -> {
                    if (app.getSettings() != null && app.getSettings().getOauth() != null) {
                        app.getSettings().getOauth().setScopeSettings(scopeSettings);
                    }
                    return app;
                })
        );// do not read grant tables, information already present into the settings object
    }

    private ApplicationIdentityProvider convertIdentity(Identity identity) {
        var idpSettings = new ApplicationIdentityProvider();
        idpSettings.setPriority(identity.getPriority());
        idpSettings.setSelectionRule(identity.getSelectionRule());
        idpSettings.setIdentity(identity.getIdentity());
        return idpSettings;
    }

    @Override
    public Flowable<Application> findAll() {
        LOGGER.debug("findAll()");
        return applicationRepository.findAll()
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable());
    }

    @Override
    public Single<Page<Application>> findAll(int page, int size) {
        LOGGER.debug("findAll({}, {})", page, size);
        return fluxToFlowable(template.select(JdbcApplication.class)
                .matching(Query.empty().with(PageRequest.of(page, size, Sort.by(COL_ID))))
                .all())
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable(), MAX_CONCURRENCY)
                .toList()
                .flatMap(data -> applicationRepository.count().map(total -> new Page<>(data, page, total)))
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications (page={}/size={})", page, size, error));
    }

    @Override
    public Flowable<Application> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return applicationRepository.findByDomain(domain)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable());
    }

    @Override
    public Single<Page<Application>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("findByDomain({}, {}, {})", domain, page, size);
        return fluxToFlowable(template.select(JdbcApplication.class)
                .matching(query(where(COL_DOMAIN).is(domain)).with(PageRequest.of(page, size, Sort.by(COL_ID))))
                .all())
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable(), MAX_CONCURRENCY)
                .toList()
                .flatMap(data -> applicationRepository.countByDomain(domain).map(total -> new Page<Application>(data, page, total)))
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with domain {} (page={}/size={})", domain, page, size, error));
    }

    @Override
    public Single<Page<Application>> search(String domain, String query, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {})", domain, query, page, size);

        boolean wildcardMatch = query.contains("*");
        String wildcardQuery = query.replaceAll("\\*+", "%");

        String search = databaseDialectHelper.buildSearchApplicationsQuery(wildcardMatch, page, size);
        String count = databaseDialectHelper.buildCountApplicationsQuery(wildcardMatch);

        return fluxToFlowable(template.getDatabaseClient().sql(search)
                .bind(COL_DOMAIN, domain)
                .bind("value", wildcardMatch ? wildcardQuery.toUpperCase() : query.toUpperCase())
                .map((row, rowMetadata) -> rowMapper.read(JdbcApplication.class, row))
                .all())
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .toList()
                .flatMap(data -> monoToSingle(template.getDatabaseClient().sql(count)
                        .bind(COL_DOMAIN, domain)
                        .bind("value", wildcardMatch ? wildcardQuery.toUpperCase() : query.toUpperCase())
                        .map((row, rowMetadata) -> row.get(0, Long.class)).first())
                        .map(total -> new Page<Application>(data, page, total)))
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with domain {} (page={}/size={})", domain, page, size, error));
    }

    @Override
    public Flowable<Application> findByCertificate(String certificate) {
        LOGGER.debug("findByCertificate({})", certificate);
        return applicationRepository.findByCertificate(certificate)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable());
    }

    @Override
    public Flowable<Application> findByIdentityProvider(String identityProvider) {
        LOGGER.debug("findByIdentityProvider({})", identityProvider);

        // identity is a keyword with mssql
        final String identity = databaseDialectHelper.toSql(SqlIdentifier.quoted("identity"));
        return fluxToFlowable(template.getDatabaseClient()
                .sql("SELECT a.* FROM applications a INNER JOIN application_identities i ON a.id = i.application_id AND i." + identity + " = :identity")
                .bind("identity", identityProvider)
                .map((row, rowMetadata) -> rowMapper.read(JdbcApplication.class, row)).all())
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable());
    }

    @Override
    public Flowable<Application> findByFactor(String factor) {
        LOGGER.debug("findByFactor({})", factor);
        return applicationRepository.findAllByFactor(factor)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable());
    }

    @Override
    public Flowable<Application> findByDomainAndExtensionGrant(String domain, String extensionGrant) {
        LOGGER.debug("findByDomainAndExtensionGrant({}, {})", domain, extensionGrant);
        return applicationRepository.findAllByDomainAndGrant(domain, extensionGrant)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable());
    }

    @Override
    public Flowable<Application> findByIdIn(List<String> ids) {
        LOGGER.debug("findByIdIn({})", ids);
        if (ids == null || ids.isEmpty()) {
            return Flowable.empty();
        }
        return applicationRepository.findByIdIn(ids)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable());
    }

    @Override
    public Single<Long> count() {
        return applicationRepository.count();
    }

    @Override
    public Single<Long> countByDomain(String domain) {
        return applicationRepository.countByDomain(domain);
    }

    @Override
    public Maybe<Application> findByDomainAndClientId(String domain, String clientId) {
        LOGGER.debug("findByDomainAndClientId({}, {})", domain, clientId);
        return fluxToFlowable(template.getDatabaseClient().sql(databaseDialectHelper.buildFindApplicationByDomainAndClient())
                .bind(COL_DOMAIN, domain)
                .bind("clientId", clientId)
                .map((row, rowMetadata) -> rowMapper.read(JdbcApplication.class, row))
                .all())
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .firstElement();
    }

    @Override
    public Maybe<Application> findById(String id) {
        LOGGER.debug("findById({}", id);
        return applicationRepository.findById(id)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toMaybe());
    }

    @Override
    public Single<Application> create(Application item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Application with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec sql = template.getDatabaseClient().sql(INSERT_STATEMENT);
        sql = addQuotedField(sql, COL_ID, item.getId(), String.class);
        sql = addQuotedField(sql, COL_TYPE, item.getType() == null ? null : item.getType().name(), String.class);
        sql = addQuotedField(sql, COL_ENABLED, item.isEnabled(), Boolean.class);
        sql = addQuotedField(sql, COL_TEMPLATE, item.isTemplate(), Boolean.class);
        sql = addQuotedField(sql, COL_NAME, item.getName(), String.class);
        sql = addQuotedField(sql, COL_DESCRIPTION, item.getDescription(), String.class);
        sql = addQuotedField(sql, COL_DOMAIN, item.getDomain(), String.class);
        sql = addQuotedField(sql, COL_CERTIFICATE, item.getCertificate(), String.class);
        sql = addQuotedField(sql, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        sql = addQuotedField(sql, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        sql = databaseDialectHelper.addJsonField(sql, COL_METADATA, item.getMetadata());
        sql = databaseDialectHelper.addJsonField(sql, COL_SETTINGS, item.getSettings());

        Mono<Long> insertAction = sql.fetch().rowsUpdated();
        insertAction = persistChildEntities(insertAction, item);

        return monoToSingle(insertAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<Application> update(Application item) {
        LOGGER.debug("Update Application with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec sql = template.getDatabaseClient().sql(UPDATE_STATEMENT);
        sql = addQuotedField(sql, COL_ID, item.getId(), String.class);
        sql = addQuotedField(sql, COL_TYPE, item.getType() == null ? null : item.getType().name(), String.class);
        sql = addQuotedField(sql, COL_ENABLED, item.isEnabled(), Boolean.class);
        sql = addQuotedField(sql, COL_TEMPLATE, item.isTemplate(), Boolean.class);
        sql = addQuotedField(sql, COL_NAME, item.getName(), String.class);
        sql = addQuotedField(sql, COL_DESCRIPTION, item.getDescription(), String.class);
        sql = addQuotedField(sql, COL_DOMAIN, item.getDomain(), String.class);
        sql = addQuotedField(sql, COL_CERTIFICATE, item.getCertificate(), String.class);
        sql = addQuotedField(sql, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        sql = addQuotedField(sql, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        sql = databaseDialectHelper.addJsonField(sql, COL_METADATA, item.getMetadata());
        sql = databaseDialectHelper.addJsonField(sql, COL_SETTINGS, item.getSettings());

        Mono<Long> updateAction = sql.fetch().rowsUpdated();
        updateAction = deleteChildEntities(item.getId()).then(updateAction);
        updateAction = persistChildEntities(updateAction, item);

        return monoToSingle(updateAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> delete = template.delete(JdbcApplication.class).matching(query(where(COL_ID).is(id))).all();
        return monoToCompletable(delete.then(deleteChildEntities(id)).as(trx::transactional))
                .andThen(applicationRepository.deleteById(id));
    }

    private Mono<Long> deleteChildEntities(String appId) {
        Mono<Integer> identities = template.delete(JdbcApplication.Identity.class).matching(query(where("application_id").is(appId))).all();
        Mono<Integer> factors = template.delete(JdbcApplication.Factor.class).matching(query(where("application_id").is(appId))).all();
        Mono<Integer> grants = template.delete(JdbcApplication.Grant.class).matching(query(where("application_id").is(appId))).all();
        Mono<Integer> scopeSettings = template.delete(JdbcApplication.ScopeSettings.class).matching(query(where("application_id").is(appId))).all();
        return factors.then(identities).then(grants).then(scopeSettings).map(Integer::longValue);
    }

    private Mono<Long> persistChildEntities(Mono<Long> actionFlow, Application app) {
        var identities = app.getIdentityProviders();
        if (identities != null && !identities.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(identities).concatMap(idp -> {
                final String identity = databaseDialectHelper.toSql(SqlIdentifier.quoted("identity"));
                final String selectionRule = databaseDialectHelper.toSql(SqlIdentifier.quoted("selection_rule"));
                final String priority = databaseDialectHelper.toSql(SqlIdentifier.quoted("priority"));
                String INSERT_STMT = "INSERT INTO application_identities" +
                        "(application_id, " + identity + ", " + selectionRule + ", " + priority + ") " +
                        "VALUES (:app, :idpid, :selection_rule, :priority)";
                final DatabaseClient.GenericExecuteSpec sql = template.getDatabaseClient()
                        .sql(INSERT_STMT)
                        .bind("app", app.getId())
                        .bind("idpid", idp.getIdentity())
                        .bind("selection_rule", idp.getSelectionRule() == null ? "" : idp.getSelectionRule())
                        .bind("priority", idp.getPriority());
                return sql.fetch().rowsUpdated();
            }).reduce(Long::sum));
        }

        final Set<String> factors = app.getFactors();
        if (factors != null && !factors.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(factors).concatMap(value -> {
                String INSERT_STMT = "INSERT INTO application_factors(application_id, factor) VALUES (:app, :factor)";
                final DatabaseClient.GenericExecuteSpec sql = template.getDatabaseClient()
                        .sql(INSERT_STMT)
                        .bind("app", app.getId())
                        .bind("factor", value);
                return sql.fetch().rowsUpdated();
            }).reduce(Long::sum));
        }

        final List<String> grants = Optional.ofNullable(app.getSettings()).map(ApplicationSettings::getOauth).map(ApplicationOAuthSettings::getGrantTypes).orElse(Collections.emptyList());
        if (grants != null && !grants.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(grants).concatMap(value -> {
                String INSERT_STMT = "INSERT INTO application_grants(application_id, grant_type) VALUES (:app, :grant)";
                final DatabaseClient.GenericExecuteSpec sql = template.getDatabaseClient()
                        .sql(INSERT_STMT)
                        .bind("app", app.getId())
                        .bind("grant", value);
                return sql.fetch().rowsUpdated();
            }).reduce(Long::sum));
        }

        final List<ApplicationScopeSettings> scopeSettings = Optional.ofNullable(app.getSettings()).map(ApplicationSettings::getOauth).map(ApplicationOAuthSettings::getScopeSettings).orElse(Collections.emptyList());
        if (scopeSettings != null && !scopeSettings.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(scopeSettings).concatMap(value -> {
                String INSERT_STMT = "INSERT INTO application_scope_settings(application_id, scope, is_default, scope_approval) VALUES (:app, :scope, :default, :approval)";
                DatabaseClient.GenericExecuteSpec sql = template.getDatabaseClient()
                        .sql(INSERT_STMT)
                        .bind("app", app.getId())
                        .bind("default", value.isDefaultScope());
                sql = value.getScope() == null ? sql.bindNull("scope", String.class) : sql.bind("scope", value.getScope());
                sql = value.getScopeApproval() == null ? sql.bindNull("approval", Integer.class) : sql.bind("approval", value.getScopeApproval());
                return sql.fetch().rowsUpdated();
            }).reduce(Long::sum));
        }

        return actionFlow;
    }

}
