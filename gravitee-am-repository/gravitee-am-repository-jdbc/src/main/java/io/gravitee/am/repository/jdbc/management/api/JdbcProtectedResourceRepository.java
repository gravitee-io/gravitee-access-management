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
import io.gravitee.am.model.McpTool;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResourceFeature;
import io.gravitee.am.model.ProtectedResourcePrimaryData;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.PageSortRequest;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource.JdbcProtectedResourceClientSecret;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource.JdbcProtectedResourceFeature;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource.JdbcProtectedResourceIdentifier;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringProtectedResourceClientSecretRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringProtectedResourceFeatureRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringProtectedResourceIdentifierRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringProtectedResourceRepository;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource.TABLE_NAME;

import static io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource.*;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

@Repository
public class JdbcProtectedResourceRepository extends AbstractJdbcRepository implements ProtectedResourceRepository {

    @Autowired
    private SpringProtectedResourceRepository spring;

    @Autowired
    private SpringProtectedResourceClientSecretRepository clientSecretSpring;

    @Autowired
    private SpringProtectedResourceFeatureRepository featuresSpring;

    @Autowired
    private SpringProtectedResourceIdentifierRepository identifierSpring;

    private interface Selects {
        String BY_DOMAIN_ID_AND_CLIENT_ID = """
                SELECT * FROM %s a WHERE
                a.domain_id = :domainId AND
                a.client_id = :clientId
                """.formatted(TABLE_NAME);
        String BY_DOMAIN_ID = """
                SELECT * FROM %s a WHERE
                a.domain_id = :domainId
        """.formatted(TABLE_NAME);
        String BY_CERTIFICATE = """
                SELECT * FROM %s a WHERE
                a.certificate = :certificateId
        """.formatted(TABLE_NAME);
    }


    protected ProtectedResource toEntity(JdbcProtectedResource entity) {
        return mapper.map(entity, ProtectedResource.class);
    }

    protected JdbcProtectedResource toJdbcEntity(ProtectedResource entity) {
        return mapper.map(entity, JdbcProtectedResource.class);
    }

    @Override
    public Maybe<ProtectedResource> findById(String id) {
        return spring.findById(id).map(this::toEntity).flatMapSingle(this::complete);
    }

    @Override
    public Maybe<ProtectedResource> findByDomainAndId(String domainId, String id) {
        LOGGER.debug("findByDomainAndId({}, {})", domainId, id);
        return spring.findByDomainIdAndId(domainId, id)
                .map(this::toEntity)
                .flatMapSingle(this::complete);
    }

    @Override
    public Single<ProtectedResource> create(ProtectedResource item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create ProtectedResource with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        Mono<ProtectedResource> insertWithChildrenUpdate = getTemplate().insert(toJdbcEntity(item)).map(this::toEntity)
                .flatMap(stored -> persistClientSecrets(item)
                        .reduceWith(() -> new ArrayList<ClientSecret>(), (list, sec) -> {
                            list.add(sec);
                            return list;
                        })
                        .doOnNext(stored::setClientSecrets)
                        .then(Mono.just(stored)))
                .flatMap(stored -> persistIdentifiers(item)
                        .map(JdbcProtectedResourceIdentifier::getIdentifier)
                        .collectList()
                        .doOnNext(stored::setResourceIdentifiers)
                        .then(Mono.just(stored)))
                .flatMap(stored -> persistFeatures(item)
                        .reduceWith(() -> new ArrayList<ProtectedResourceFeature>(), (list, sec) -> {
                            list.add(sec);
                            return list;
                        })
                        .doOnNext(stored::setFeatures)
                        .then(Mono.just(stored)));

        return monoToSingle(insertWithChildrenUpdate.as(trx::transactional));
    }

    @Override
    public Single<ProtectedResource> update(ProtectedResource item) {
        LOGGER.debug("Update ProtectedResource with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        Mono<Long> updateAction = getTemplate().update(toJdbcEntity(item)).map(updated -> 1L);

        updateAction = deleteIdentifiers(item.getId()).then(updateAction);
        updateAction = persistIdentifiers(updateAction, item);
        updateAction = deleteFeatures(item.getId()).then(updateAction);
        updateAction = persistFeatures(updateAction, item);
        updateAction = deleteClientSecrets(item.getId()).then(updateAction);
        updateAction = persistClientSecrets(updateAction, item);

        return monoToSingle(updateAction.as(trx::transactional))
                .flatMap(i -> this.findById(item.getId()).toSingle());
    }

    private Mono<Long> deleteFeatures(String protectedResourceId) {
        final Query criteria = Query.query(where(JdbcProtectedResourceFeature.FIELD_PROTECTED_RESOURCE_ID).is(protectedResourceId));
        return getTemplate().delete(criteria, JdbcProtectedResourceFeature.class);
    }

    private Mono<Long> persistFeatures(Mono<Long> actionFlow, ProtectedResource item) {
        if (item.getFeatures() != null && !item.getFeatures().isEmpty()) {
            return actionFlow.then(
                    persistFeatures(item)
                            .count()  // Count the features persisted
            );
        }
        return actionFlow;
    }

    @Override
    public Completable delete(String s) {
        LOGGER.debug("delete({})", s);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> deleteChildren = Mono.when(
                getTemplate().delete(query(where(JdbcProtectedResourceClientSecret.FIELD_PROTECTED_RESOURCE_ID).is(s)), JdbcProtectedResourceClientSecret.class),
                getTemplate().delete(query(where(JdbcProtectedResourceFeature.FIELD_PROTECTED_RESOURCE_ID).is(s)), JdbcProtectedResourceFeature.class),
                getTemplate().delete(query(where(JdbcProtectedResourceIdentifier.FIELD_PROTECTED_RESOURCE_ID).is(s)), JdbcProtectedResourceIdentifier.class)
        ).thenReturn(0L);
        Mono<Long> deleteParent = getTemplate().delete(JdbcProtectedResource.class)
                .matching(query(where(COLUMN_ID).is(s))).all();
        return monoToCompletable(deleteChildren.then(deleteParent).as(trx::transactional));
    }


    @Override
    public Maybe<ProtectedResource> findByDomainAndClient(String domainId, String clientId) {

        LOGGER.debug("findByDomainAndClientId({}, {})", domainId, clientId);
        return fluxToFlowable(getTemplate().getDatabaseClient()
                .sql(Selects.BY_DOMAIN_ID_AND_CLIENT_ID)
                .bind("domainId", domainId)
                .bind("clientId", clientId)
                .map((row, rowMetadata) -> rowMapper.read(JdbcProtectedResource.class, row))
                .all())
                .map(this::toEntity)
                .flatMap(app -> complete(app).toFlowable())
                .firstElement();
    }

    @Override
    public Flowable<ProtectedResource> findAll() {
        Flowable<ProtectedResource> resources = this.spring.findAll().map(this::toEntity);
        return attachIdentifiers(attachSecrets(resources));
    }

    @Override
    public Flowable<ProtectedResource> findByDomain(String domain) {
        Flowable<ProtectedResource> resources = fluxToFlowable(getTemplate().getDatabaseClient()
                .sql(Selects.BY_DOMAIN_ID)
                .bind("domainId", domain)
                .map((row, rowMetadata) -> rowMapper.read(JdbcProtectedResource.class, row))
                .all())
                .map(this::toEntity);
        return attachIdentifiers(attachSecrets(resources));
    }

    @Override
    public Flowable<ProtectedResource> findByCertificate(String certificateId) {
        LOGGER.debug("findByCertificate({})", certificateId);
        Flowable<ProtectedResource> resources = fluxToFlowable(getTemplate().getDatabaseClient()
                .sql(Selects.BY_CERTIFICATE)
                .bind("certificateId", certificateId)
                .map((row, rowMetadata) -> rowMapper.read(JdbcProtectedResource.class, row))
                .all())
                .map(this::toEntity);
        return attachIdentifiers(attachSecrets(resources));
    }

    private Flowable<ProtectedResource> attachSecrets(Flowable<ProtectedResource> resourcesFlow) {
        return resourcesFlow
                .toList()
                .flatMapPublisher(resources -> {
                    if (resources.isEmpty()) {
                        return Flowable.empty();
                    }
                    List<String> ids = resources.stream().map(ProtectedResource::getId).toList();
                    return clientSecretSpring.findAllByProtectedResourceIdIn(ids)
                            .toList()
                            .flatMapPublisher(secrets -> {
                                Map<String, List<JdbcProtectedResourceClientSecret>> byResourceId = secrets
                                        .stream()
                                        .collect(Collectors.groupingBy(JdbcProtectedResourceClientSecret::getProtectedResourceId));

                                resources.forEach(res -> {
                                    List<JdbcProtectedResourceClientSecret> secretsForRes = byResourceId.get(res.getId());
                                    List<ClientSecret> mapped = secretsForRes == null ? List.of() : secretsForRes.stream()
                                            .map(j -> mapper.map(j, ClientSecret.class))
                                            .toList();
                                    res.setClientSecrets(mapped);
                                });
                                return Flowable.fromIterable(resources);
                            });
                });
    }

    private Flowable<ProtectedResource> attachIdentifiers(Flowable<ProtectedResource> resourcesFlow) {
        return resourcesFlow
                .toList()
                .flatMapPublisher(resources -> {
                    if (resources.isEmpty()) {
                        return Flowable.empty();
                    }
                    List<String> ids = resources.stream().map(ProtectedResource::getId).toList();
                    return identifierSpring.findAllByProtectedResourceIdIn(ids)
                            .toList()
                            .flatMapPublisher(identifiers -> {
                                Map<String, List<JdbcProtectedResourceIdentifier>> byResourceId = identifiers
                                        .stream()
                                        .collect(Collectors.groupingBy(JdbcProtectedResourceIdentifier::getProtectedResourceId));

                                resources.forEach(res -> {
                                    List<JdbcProtectedResourceIdentifier> identifiersForRes = byResourceId.get(res.getId());
                                    List<String> mapped = identifiersForRes == null ? List.of() : identifiersForRes.stream()
                                            .map(JdbcProtectedResourceIdentifier::getIdentifier)
                                            .toList();
                                    res.setResourceIdentifiers(mapped);
                                });
                                return Flowable.fromIterable(resources);
                            });
                });
    }

    private Flowable<ProtectedResource> attachFeatures(Flowable<ProtectedResource> resourcesFlow) {
        return resourcesFlow
                .toList()
                .flatMapPublisher(resources -> {
                    if (resources.isEmpty()) {
                        return Flowable.empty();
                    }
                    List<String> ids = resources.stream().map(ProtectedResource::getId).toList();
                    return featuresSpring.findAllByProtectedResourceIdInOrderByKeyName(ids)
                            .toList()
                            .flatMapPublisher(features -> {
                                Map<String, List<JdbcProtectedResourceFeature>> byResourceId = features
                                        .stream()
                                        .collect(Collectors.groupingBy(JdbcProtectedResourceFeature::getProtectedResourceId));

                                resources.forEach(res -> {
                                    List<JdbcProtectedResourceFeature> featuresForRes = byResourceId.get(res.getId());
                                    List<McpTool> mapped = featuresForRes == null ? List.of() : featuresForRes.stream()
                                            .map(feature ->  mapper.map(feature, McpTool.class))
                                            .toList();
                                    res.setFeatures(mapped);
                                });
                                return Flowable.fromIterable(resources);
                            });
                });
    }

    private String transformSortValue(String sort) {
        return switch (sort) {
            case "name" -> COLUMN_NAME;
            case "updatedAt" -> COLUMN_UPDATED_AT;
            default -> COLUMN_UPDATED_AT;
        };
    }

    @Override
    public Single<Page<ProtectedResourcePrimaryData>> findByDomainAndType(String domainId, ProtectedResource.Type type, PageSortRequest pageSortRequest) {
        String sortBy = pageSortRequest.getSortBy().orElse(COLUMN_UPDATED_AT);
        Sort.Order order = pageSortRequest.isAsc() ? Sort.Order.asc(transformSortValue(sortBy)) : Sort.Order.desc(transformSortValue(sortBy));
        Sort sort = Sort.by(order);
        Flowable<ProtectedResource> mainFlowable = fluxToFlowable(getTemplate().select(JdbcProtectedResource.class)
                .matching(query(where(COLUMN_DOMAIN_ID).is(domainId).and(COLUMN_TYPE).is(type))
                        .with(PageRequest.of(pageSortRequest.getPage(), pageSortRequest.getSize(), sort)))
                .all())
                .map(this::toEntity);
        return attachIdentifiers(attachFeatures(mainFlowable))
                .map(ProtectedResourcePrimaryData::of)
                .toList()
                .flatMap(data -> spring.countByDomainIdAndType(domainId, type).map(total -> new Page<>(data, pageSortRequest.getPage(), total)))
                .doOnError(error -> LOGGER.error("Unable to retrieve all protected resources with domainId={}, type={} (page={}/size={})", domainId, type, pageSortRequest.getPage(), pageSortRequest.getSize(), error));
    }

    @Override
    public Single<Page<ProtectedResourcePrimaryData>> findByDomainAndTypeAndIds(String domainId, ProtectedResource.Type type, List<String> ids, PageSortRequest pageSortRequest) {
        if (ids.isEmpty()) {
            return Single.just(new Page<>());
        }
        String sortBy = pageSortRequest.getSortBy().orElse(COLUMN_UPDATED_AT);
        Sort.Order order = pageSortRequest.isAsc() ? Sort.Order.asc(transformSortValue(sortBy)) : Sort.Order.desc(transformSortValue(sortBy));
        Sort sort = Sort.by(order);
        Flowable<ProtectedResource> mainFlowable = fluxToFlowable(getTemplate().select(JdbcProtectedResource.class)
                .matching(query(where(COLUMN_DOMAIN_ID).is(domainId).and(COLUMN_TYPE).is(type).and(COLUMN_ID).in(ids))
                        .with(PageRequest.of(pageSortRequest.getPage(), pageSortRequest.getSize(), sort)))
                .all())
                .map(this::toEntity);
        return attachIdentifiers(attachFeatures(mainFlowable))
                .map(ProtectedResourcePrimaryData::of)
                .toList()
                .flatMap(data -> spring.countByDomainIdAndTypeAndIdIn(domainId, type, ids).map(total -> new Page<>(data, pageSortRequest.getPage(), total)))
                .doOnError(error -> LOGGER.error("Unable to retrieve all protected resources with domainId={}, type={} (page={}/size={})", domainId, type, pageSortRequest.getPage(), pageSortRequest.getSize(), error));
    }

    @Override
    public Single<Boolean> existsByResourceIdentifiers(String domainId, List<String> resourceIdentifiers) {
        if (resourceIdentifiers == null || resourceIdentifiers.isEmpty()) {
            return Single.just(false);
        }
        return identifierSpring.existsByDomainIdAndIdentifierIn(domainId, resourceIdentifiers);
    }

    @Override
    public Single<Boolean> existsByResourceIdentifiersExcludingId(String domainId, List<String> resourceIdentifiers, String excludeId) {
        if (resourceIdentifiers == null || resourceIdentifiers.isEmpty()) {
            return Single.just(false);
        }
        return identifierSpring.existsByDomainIdAndIdentifierInAndProtectedResourceIdNot(domainId, resourceIdentifiers, excludeId);
    }

    private Single<ProtectedResource> complete(ProtectedResource entity) {
        return Single.just(entity)
                .flatMap(app -> clientSecretSpring.findAllByProtectedResourceId(app.getId())
                        .map(jdbcClientSecret -> mapper.map(jdbcClientSecret, ClientSecret.class))
                        .toList()
                        .map(secrets -> {
                            app.setClientSecrets(secrets);
                            return app;
                        }))
                .flatMap(app -> identifierSpring.findAllByProtectedResourceId(app.getId())
                        .map(JdbcProtectedResourceIdentifier::getIdentifier)
                        .toList()
                        .map(identifiers -> {
                            app.setResourceIdentifiers(identifiers);
                            return app;
                        }))
                .flatMap(app -> featuresSpring.findAllByProtectedResourceIdOrderByKeyName(app.getId())
                        .map(feature -> mapper.map(feature, McpTool.class))
                        .toList()
                        .map(features -> {
                            app.setFeatures(features);
                            return app;
                        }));
    }

    private Flux<ClientSecret> persistClientSecrets(ProtectedResource protectedResource) {
        List<JdbcProtectedResourceClientSecret> jdbcSecrets = protectedResource.getClientSecrets() == null ? List.of() : protectedResource.getClientSecrets()
                .stream()
                .map(secret -> mapper.map(secret, JdbcProtectedResourceClientSecret.class))
                .map(secret -> {
                    secret.setProtectedResourceId(protectedResource.getId());
                    return secret;
                })
                .toList();
        return Flux.fromIterable(jdbcSecrets)
                .concatMap(jdbc -> getTemplate().insert(jdbc))
                .map(jdbc -> mapper.map(jdbc, ClientSecret.class));
    }

    private Mono<Long> deleteIdentifiers(String protectedResourceId) {
        final Query criteria = Query.query(where(JdbcProtectedResourceIdentifier.FIELD_PROTECTED_RESOURCE_ID).is(protectedResourceId));
        return getTemplate().delete(criteria, JdbcProtectedResourceIdentifier.class);
    }

    private Mono<Long> persistIdentifiers(Mono<Long> actionFlow, ProtectedResource item) {
        if (item.getResourceIdentifiers() != null && !item.getResourceIdentifiers().isEmpty()) {
            return actionFlow.then(
                    persistIdentifiers(item)
                            .count()  // Count the identifiers persisted
            );
        }
        return actionFlow;
    }

    private Flux<JdbcProtectedResourceIdentifier> persistIdentifiers(ProtectedResource protectedResource) {
        List<JdbcProtectedResourceIdentifier> resourceIdentifiers = protectedResource.getResourceIdentifiers() == null ? List.of() : protectedResource.getResourceIdentifiers()
                .stream()
                .map(identifier -> {
                    JdbcProtectedResourceIdentifier resourceIdentifier = new JdbcProtectedResourceIdentifier();
                    resourceIdentifier.setId(RandomString.generate());
                    resourceIdentifier.setProtectedResourceId(protectedResource.getId());
                    resourceIdentifier.setIdentifier(identifier);
                    resourceIdentifier.setDomainId(protectedResource.getDomainId());
                    return resourceIdentifier;
                })
                .toList();

        return Flux.fromIterable(resourceIdentifiers)
                .concatMap(jdbc -> getTemplate().insert(jdbc));
    }

    private Mono<Long> deleteClientSecrets(String protectedResourceId) {
        final Query criteria = Query.query(where(JdbcProtectedResourceClientSecret.FIELD_PROTECTED_RESOURCE_ID).is(protectedResourceId));
        return getTemplate().delete(criteria, JdbcProtectedResourceClientSecret.class);
    }

    private Mono<Long> persistClientSecrets(Mono<Long> actionFlow, ProtectedResource item) {
        if (item.getClientSecrets() != null && !item.getClientSecrets().isEmpty()) {
            return actionFlow.then(
                    persistClientSecrets(item)
                            .count()  // Count the secrets persisted
            );
        }
        return actionFlow;
    }

    private Flux<ProtectedResourceFeature> persistFeatures(ProtectedResource protectedResource) {
        List<JdbcProtectedResourceFeature> jdbcFeatures = protectedResource.getFeatures() == null ? List.of() : protectedResource.getFeatures()
                .stream()
                .map(feature -> mapper.map(feature, JdbcProtectedResource.JdbcProtectedResourceFeature.class))
                .map(feature -> {
                    feature.setId(RandomString.generate());
                    feature.setProtectedResourceId(protectedResource.getId());
                    return feature;
                })
                .toList();


        return Flux.fromIterable(jdbcFeatures)
                .concatMap(jdbc -> getTemplate().insert(jdbc))
                .map(jdbc -> mapper.map(jdbc, ProtectedResourceFeature.class));
    }
}
