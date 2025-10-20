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
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcApplication;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource.JdbcProtectedResourceClientSecret;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringProtectedResourceClientSecretRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringProtectedResourceRepository;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.ReactiveDeleteOperation;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static io.gravitee.am.repository.jdbc.management.api.JdbcApplicationRepository.COL_CREATED_AT;
import static io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource.JdbcProtectedResourceClientSecret.FIELD_PROTECTED_RESOURCE_ID;
import static io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource.TABLE_NAME;
import static java.util.stream.Collectors.toCollection;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

@Repository
public class JdbcProtectedResourceRepository extends AbstractJdbcRepository implements ProtectedResourceRepository {

    @Autowired
    private SpringProtectedResourceRepository spring;

    @Autowired
    private SpringProtectedResourceClientSecretRepository clientSecretSpring;

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
    }


    protected ProtectedResource toEntity(JdbcProtectedResource entity) {
        return mapper.map(entity, ProtectedResource.class);
    }

    protected JdbcProtectedResource toJdbcEntity(ProtectedResource entity) {
        return mapper.map(entity, JdbcProtectedResource.class);
    }

    public Maybe<ProtectedResource> findById(String id) {
        return spring.findById(id).map(this::toEntity).flatMapSingle(this::complete);
    }

    @Override
    public Single<ProtectedResource> create(ProtectedResource item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create ProtectedResource with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        Mono<ProtectedResource> insertWithChildrenUpdate = getTemplate().insert(toJdbcEntity(item)).map(this::toEntity)
                .flatMap(stored -> persistChildEntities(item)
                        .reduceWith(() -> new ArrayList<ClientSecret>(), (list, sec) -> {
                            list.add(sec);
                            return list;
                        })
                        .doOnNext(stored::setClientSecrets)
                        .then(Mono.just(stored)));

        return monoToSingle(insertWithChildrenUpdate.as(trx::transactional));
    }

    @Override
    public Single<ProtectedResource> update(ProtectedResource item) {
        return Single.just(item); // TODO AM-5756
    }

    @Override
    public Completable delete(String s) {
        return Completable.complete(); // TODO AM-5757
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
        return this.spring.findAll().map(this::toEntity).flatMap(app -> complete(app).toFlowable());
    }

    @Override
    public Flowable<ProtectedResource> findByDomain(String domain) {
        return fluxToFlowable(getTemplate().getDatabaseClient()
                .sql(Selects.BY_DOMAIN_ID)
                .bind("domainId", domain)
                .map((row, rowMetadata) -> rowMapper.read(JdbcProtectedResource.class, row))
                .all())
                .map(this::toEntity);
    }

    private Single<ProtectedResource> complete(ProtectedResource entity) {
        return Single.just(entity).flatMap(app ->
                clientSecretSpring.findAllByProtectedResourceId(app.getId())
                        .map(jdbcClientSecret -> mapper.map(jdbcClientSecret, ClientSecret.class))
                        .toList()
                        .map(secrets -> {
                            app.setClientSecrets(secrets);
                            return app;
                        }));
    }

    private Flux<ClientSecret> persistChildEntities(ProtectedResource protectedResource) {
        List<JdbcProtectedResourceClientSecret> jdbcSecrets = protectedResource.getClientSecrets() == null ? List.of() : protectedResource.getClientSecrets()
                .stream()
                .map(secret -> mapper.map(secret, JdbcProtectedResourceClientSecret.class))
                .map(secret -> {
                    secret.setProtectedResourceId(protectedResource.getId());
                    return secret;
                })
                .toList();
        Mono<Long> deleteSecrets = getTemplate()
                .delete(Query.query(Criteria.where(FIELD_PROTECTED_RESOURCE_ID).is(protectedResource.getId())), JdbcProtectedResourceClientSecret.class);
        Flux<ClientSecret> saveSecrets = Flux.fromIterable(jdbcSecrets)
                .concatMap(jdbc -> getTemplate().insert(jdbc))
                .map(jdbc -> mapper.map(jdbc, ClientSecret.class));
        return deleteSecrets.thenMany(saveSecrets);
    }
}
