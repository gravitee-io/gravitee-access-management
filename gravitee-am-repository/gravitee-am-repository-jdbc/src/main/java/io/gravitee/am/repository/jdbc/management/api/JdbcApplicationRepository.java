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
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcApplication;
import io.gravitee.am.repository.jdbc.management.api.spring.application.SpringApplicationFactorRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.application.SpringApplicationIdentityRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.application.SpringApplicationRepository;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcApplicationRepository extends AbstractJdbcRepository implements ApplicationRepository {
    public static final int MAX_CONCURRENCY = 1;

    @Autowired
    private SpringApplicationRepository applicationRepository;

    @Autowired
    private SpringApplicationFactorRepository factorRepository;

    @Autowired
    private SpringApplicationIdentityRepository identityRepository;

    protected Application toEntity(JdbcApplication entity) {
        return mapper.map(entity, Application.class);
    }

    protected JdbcApplication toJdbcEntity(Application entity) {
        return mapper.map(entity, JdbcApplication.class);
    }

    private Single<Application> completeApplication(Application entity) {
        return Single.just(entity).flatMap(app ->
                identityRepository.findAllByApplicationId(app.getId()).map(JdbcApplication.Identity::getIdentity).toList().map(idps -> {
                    app.setIdentities(new HashSet<>(idps));
                    return app;
                })
        ).flatMap(app ->
                factorRepository.findAllByApplicationId(app.getId()).map(JdbcApplication.Factor::getFactor).toList().map(factors -> {
                    app.setFactors(new HashSet<>(factors));
                    return app;
                })
        );// do not read grant tables, information already present into the settings object
    }

    @Override
    public Single<List<Application>> findAll() {
        LOGGER.debug("findAll()");
        return applicationRepository.findAll()
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications", error))
                .toList();
    }

    @Override
    public Single<Page<Application>> findAll(int page, int size) {
        LOGGER.debug("findAll({}, {})", page, size);
        return fluxToFlowable(dbClient.select()
                .from(JdbcApplication.class)
                .page(PageRequest.of(page, size, Sort.by("id")))
                .as(JdbcApplication.class)
                .all())
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable(), MAX_CONCURRENCY)
                .toList()
                .flatMap(data -> applicationRepository.count().map(total -> new Page<Application>(data, page, total)))
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications (page={}/size={})", page, size, error));
    }

    @Override
    public Single<List<Application>> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})",domain);
        return applicationRepository.findByDomain(domain)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with domain {}", domain, error))
                .toList();
    }

    @Override
    public Single<Page<Application>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("findByDomain({}, {}, {})", domain, page, size);
        return fluxToFlowable(dbClient.select()
                .from(JdbcApplication.class)
                .matching(from(where("domain").is(domain)))
                .page(PageRequest.of(page, size, Sort.by("id")))
                .as(JdbcApplication.class)
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

        return fluxToFlowable(dbClient.execute(search)
                .bind("domain", domain)
                .bind("value", wildcardMatch ? wildcardQuery.toUpperCase() : query.toUpperCase())
                .as(JdbcApplication.class)
                .fetch()
                .all())
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .toList()
                .flatMap(data -> monoToSingle(dbClient.execute(count)
                        .bind("domain", domain)
                        .bind("value", wildcardMatch ? wildcardQuery.toUpperCase() : query.toUpperCase())
                        .as(Long.class)
                        .fetch()
                        .first())
                        .map(total -> new Page<Application>(data, page, total)))
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with domain {} (page={}/size={})", domain, page, size, error));
    }

    @Override
    public Single<Set<Application>> findByCertificate(String certificate) {
        LOGGER.debug("findByCertificate({})", certificate);
        return applicationRepository.findByCertificate(certificate)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with certificate {}", certificate, error))
                .toList().map(list -> list.stream().collect(Collectors.toSet()));
    }

    @Override
    public Single<Set<Application>> findByIdentityProvider(String identityProvider) {
        LOGGER.debug("findByIdentityProvider({})", identityProvider);

        // identity is a keyword with mssql
        return fluxToFlowable(dbClient.execute("SELECT a.* FROM applications a INNER JOIN application_identities i ON a.id = i.application_id where i." +
                databaseDialectHelper.toSql(SqlIdentifier.quoted("identity")) + " = :identity")
                .bind("identity", identityProvider).as(JdbcApplication.class).fetch().all())
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with identityProvider {}", identityProvider, error))
                .toList().map(list -> list.stream().collect(Collectors.toSet()));
    }

    @Override
    public Single<Set<Application>> findByFactor(String factor) {
        LOGGER.debug("findByFactor({})", factor);
        return applicationRepository.findAllByFactor(factor)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with factor {}", factor, error))
                .toList().map(list -> list.stream().collect(Collectors.toSet()));
    }

    @Override
    public Single<Set<Application>> findByDomainAndExtensionGrant(String domain, String extensionGrant) {
        LOGGER.debug("findByDomainAndExtensionGrant({}, {})", domain, extensionGrant);
        return applicationRepository.findAllByDomainAndGrant(domain, extensionGrant)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .toList()
                .map(list -> list.stream().collect(Collectors.toSet()))
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with domain {})", domain, error));
    }

    @Override
    public Single<Set<Application>> findByIdIn(List<String> ids) {
        LOGGER.debug("findByIdIn({})", ids);
        if (ids == null || ids.isEmpty()) {
            return Single.just(Collections.emptySet());
        }
        return applicationRepository.findByIdIn(ids)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with ids {}", ids, error))
                .toList().map(list -> list.stream().collect(Collectors.toSet()));
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
        return fluxToFlowable(dbClient.execute(databaseDialectHelper.buildFindApplicationByDomainAndClient())
                .bind("domain", domain)
                .bind("clientId", clientId)
                .as(JdbcApplication.class)
                .fetch()
                .all())
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toFlowable())
                .firstElement()
                .doOnError((error) -> LOGGER.error("Unable to retrieve all applications with domain {})", domain, error));
    }

    @Override
    public Maybe<Application> findById(String id) {
        LOGGER.debug("findById({}", id);
        return applicationRepository.findById(id)
                .map(this::toEntity)
                .flatMap(app -> completeApplication(app).toMaybe())
                .doOnError(error -> LOGGER.error("Unable to retrieve application with id {}", id, error));
    }

    @Override
    public Single<Application> create(Application item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Application with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into("applications");

        // doesn't use the class introspection to handle json objects
        insertSpec = addQuotedField(insertSpec,"id", item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec,"type", item.getType() == null ? null : item.getType().name(), String.class);
        insertSpec = addQuotedField(insertSpec,"enabled", item.isEnabled(), Boolean.class);
        insertSpec = addQuotedField(insertSpec,"template", item.isTemplate(), Boolean.class);
        insertSpec = addQuotedField(insertSpec,"name", item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec,"description", item.getDescription(), String.class);
        insertSpec = addQuotedField(insertSpec,"domain", item.getDomain(), String.class);
        insertSpec = addQuotedField(insertSpec,"certificate", item.getCertificate(), String.class);
        insertSpec = addQuotedField(insertSpec,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec,"metadata", item.getMetadata());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec,"settings", item.getSettings());

        Mono<Integer> insertAction = insertSpec.fetch().rowsUpdated();

        insertAction = persistChildEntities(insertAction, item);

        return monoToSingle(insertAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create application with id {}", item.getId(), error));
    }

    @Override
    public Single<Application> update(Application item) {
        LOGGER.debug("Update Application with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        final DatabaseClient.GenericUpdateSpec updateSpec = dbClient.update().table("applications");

        // doesn't use the class introspection to handle json objects
        Map<SqlIdentifier, Object> updateFields = new HashMap<>();
        updateFields = addQuotedField(updateFields,"id", item.getId(), String.class);
        updateFields = addQuotedField(updateFields,"type", item.getType() == null ? null : item.getType().name(), String.class);
        updateFields = addQuotedField(updateFields,"enabled", item.isEnabled(), Boolean.class);
        updateFields = addQuotedField(updateFields,"template", item.isTemplate(), Boolean.class);
        updateFields = addQuotedField(updateFields,"name", item.getName(), String.class);
        updateFields = addQuotedField(updateFields,"description", item.getDescription(), String.class);
        updateFields = addQuotedField(updateFields,"domain", item.getDomain(), String.class);
        updateFields = addQuotedField(updateFields,"certificate", item.getCertificate(), String.class);
        updateFields = addQuotedField(updateFields,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        updateFields = databaseDialectHelper.addJsonField(updateFields,"metadata", item.getMetadata());
        updateFields = databaseDialectHelper.addJsonField(updateFields,"settings", item.getSettings());

        Mono<Integer> updateAction = updateSpec.using(Update.from(updateFields)).matching(from(where("id").is(item.getId()))).fetch().rowsUpdated();

        updateAction = deleteChildEntities(item.getId()).then(updateAction);
        updateAction = persistChildEntities(updateAction, item);

        return monoToSingle(updateAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to update application with id {}", item.getId(), error));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> delete = dbClient.delete().from(JdbcApplication.class).matching(from(where("id").is(id))).fetch().rowsUpdated();
        return monoToCompletable(delete.then(deleteChildEntities(id)).as(trx::transactional))
                .andThen(applicationRepository.deleteById(id))
                .doOnError(error -> LOGGER.error("Unable to delete Application with id {}", id, error));
    }

    private Mono<Integer> deleteChildEntities(String appId) {
        Mono<Integer> identities = dbClient.delete().from(JdbcApplication.Identity.class).matching(from(where("application_id").is(appId))).fetch().rowsUpdated();
        Mono<Integer> factors = dbClient.delete().from(JdbcApplication.Factor.class).matching(from(where("application_id").is(appId))).fetch().rowsUpdated();
        Mono<Integer> grants = dbClient.delete().from(JdbcApplication.Grant.class).matching(from(where("application_id").is(appId))).fetch().rowsUpdated();
        return factors.then(identities).then(grants);
    }

    private Mono<Integer> persistChildEntities(Mono<Integer> actionFlow, Application item) {
        final Set<String> identities = item.getIdentities();
        if (identities != null && !identities.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(identities).concatMap(idp -> {
                JdbcApplication.Identity identity = new JdbcApplication.Identity();
                identity.setIdentity(idp);
                identity.setApplicationId(item.getId());

                DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into("application_identities");
                insertSpec = addQuotedField(insertSpec,"application_id", identity.getApplicationId(), String.class);
                insertSpec = addQuotedField(insertSpec,"identity", identity.getIdentity(), String.class);

                return insertSpec.fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        final Set<String> factors = item.getFactors();
        if (factors != null && !factors.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(factors).concatMap(value -> {
                JdbcApplication.Factor factor = new JdbcApplication.Factor();
                factor.setFactor(value);
                factor.setApplicationId(item.getId());
                return dbClient.insert().into(JdbcApplication.Factor.class).using(factor).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        final List<String> grants = Optional.ofNullable(item.getSettings()).map(ApplicationSettings::getOauth).map(ApplicationOAuthSettings::getGrantTypes).orElse(Collections.emptyList());
        if (grants != null && !grants.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(grants).concatMap(value -> {
                JdbcApplication.Grant grant = new JdbcApplication.Grant();
                grant.setGrant(value);
                grant.setApplicationId(item.getId());
                return dbClient.insert().into(JdbcApplication.Grant.class).using(grant).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        return actionFlow;
    }

}
