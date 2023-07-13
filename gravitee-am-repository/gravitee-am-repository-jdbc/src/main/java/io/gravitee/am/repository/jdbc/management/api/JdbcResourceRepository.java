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
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcResource;
import io.gravitee.am.repository.jdbc.management.api.spring.resources.SpringResourceRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.resources.SpringResourceScopeRepository;
import io.gravitee.am.repository.management.api.ResourceRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.CriteriaDefinition;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.In;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcResourceRepository extends AbstractJdbcRepository implements ResourceRepository {

    public static final int MAX_CONCURRENCY = 1;
    @Autowired
    protected SpringResourceRepository resourceRepository;

    @Autowired
    protected SpringResourceScopeRepository resourceScopeRepository;

    protected Resource toEntity(JdbcResource entity) {
        return mapper.map(entity, Resource.class);
    }

    protected JdbcResource toJdbcEntity(Resource entity) {
        return mapper.map(entity, JdbcResource.class);
    }

    @Override
    public Single<Page<Resource>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("findByDomain({}, {}, {})", domain, page, size);
        CriteriaDefinition whereClause = from(where("domain").is(domain));
        return findResourcePage(domain, page, size, whereClause);
    }

    private Single<Page<Resource>> findResourcePage(String domain, int page, int size, CriteriaDefinition whereClause) {
        return fluxToFlowable(template.select(Query.query(whereClause)
                                .sort(Sort.by("id").ascending())
                                .with(PageRequest.of(page, size)), JdbcResource.class))
                .map(this::toEntity)
                .flatMap(res -> completeWithScopes(Maybe.just(res), res.getId()).toFlowable(), MAX_CONCURRENCY)
                .toList()
                .flatMap(content -> resourceRepository.countByDomain(domain)
                        .map((count) -> new Page<Resource>(content, page, count)));
    }

    private Maybe<Resource> completeWithScopes(Maybe<Resource> maybeResource, String id) {
        Maybe<List<String>> scopes = resourceScopeRepository.findAllByResourceId(id)
                .map(JdbcResource.Scope::getScope)
                .toList()
                .toMaybe();

        return maybeResource.zipWith(scopes, (res, scope) -> {
                    LOGGER.debug("findById({}) fetch {} resource scopes", id, scope == null ? 0 : scope.size());
                    res.setResourceScopes(scope);
                    return res;
                });
    }

    @Override
    public Single<Page<Resource>> findByDomainAndClient(String domain, String client, int page, int size) {
        LOGGER.debug("findByDomainAndClient({}, {}, {}, {})", domain, client, page, size);
        CriteriaDefinition whereClause = from(where("domain").is(domain).and(where("client_id").is(client)));
        return findResourcePage(domain, page, size, whereClause);
    }

    @Override
    public Flowable<Resource> findByResources(List<String> resources) {
        LOGGER.debug("findByResources({})", resources);
        if (resources == null || resources.isEmpty()) {
            return Flowable.empty();
        }
        return resourceRepository.findByIdIn(resources)
                .map(this::toEntity)
                .flatMap(resource -> completeWithScopes(Maybe.just(resource), resource.getId()).toFlowable());
    }

    @Override
    public Flowable<Resource> findByDomainAndClientAndUser(String domain, String client, String userId) {
        LOGGER.debug("findByDomainAndClientAndUser({},{},{})", domain, client, userId);
        return resourceRepository.findByDomainAndClientAndUser(domain, client, userId)
                .map(this::toEntity)
                .flatMap(resource -> completeWithScopes(Maybe.just(resource), resource.getId()).toFlowable());
    }

    @Override
    public Flowable<Resource> findByDomainAndClientAndResources(String domain, String client, List<String> resources) {
        LOGGER.debug("findByDomainAndClientAndUser({},{},{})", domain, client, resources);
        return resourceRepository.findByDomainAndClientAndResources(domain, client, resources)
                .map(this::toEntity)
                .flatMap(resource -> completeWithScopes(Maybe.just(resource), resource.getId()).toFlowable());
    }

    @Override
    public Maybe<Resource> findByDomainAndClientAndUserAndResource(String domain, String client, String userId, String resource) {
        LOGGER.debug("findByDomainAndClientAndUserAndResource({},{},{},{})", domain, client, userId, resource);
        return completeWithScopes(resourceRepository.findByDomainAndClientAndUserIdAndResource(domain, client, userId, resource)
                .map(this::toEntity), resource);
    }

    @Override
    public Maybe<Resource> findById(String id) {
        LOGGER.debug("findById({})", id);
        return completeWithScopes(resourceRepository.findById(id).map(this::toEntity), id);
    }

    @Override
    public Single<Resource> create(Resource item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create Resource with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> insertResult = template.insert(toJdbcEntity(item)).map(__ -> 1L);

        final List<String> resourceScopes = item.getResourceScopes();
        if (resourceScopes != null && !resourceScopes.isEmpty()) {
            insertResult = insertResult.then(Flux.fromIterable(resourceScopes)
                    .concatMap(scope -> insertScope(item, scope))
                    .reduce(Long::sum));
        }

        return monoToSingle(insertResult.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<Resource> update(Resource item) {
        LOGGER.debug("update Resource with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> deleteScopes = template.delete(JdbcResource.Scope.class)
                .matching(Query.query(where("resource_id").is(item.getId()))).all().map(Integer::longValue);

        Mono<Long> updateResource = template.update(toJdbcEntity(item)).map(__ -> 1L);
        final List<String> resourceScopes = item.getResourceScopes();
        if (resourceScopes != null && !resourceScopes.isEmpty()) {
            updateResource = updateResource.then(Flux.fromIterable(resourceScopes)
                    .concatMap(scope -> insertScope(item, scope))
                    .reduce(Long::sum));
        }

        return monoToSingle(deleteScopes.then(updateResource).as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    private Mono<Long> insertScope(Resource item, String scope) {
        return template.getDatabaseClient()
                .sql("INSERT INTO uma_resource_scopes(resource_id, scope) VALUES (:resource_id, :scope)")
                .bind("resource_id", item.getId())
                .bind("scope", scope)
                .fetch().rowsUpdated();
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("Delete Resource with id {}", id);

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> deleteScopes = template.delete(JdbcResource.Scope.class)
                .matching(Query.query(where("resource_id").is(id))).all().map(Integer::longValue);

        Mono<Long> delete = template.delete(JdbcResource.class)
                .matching(Query.query(where("id").is(id))).all().map(Integer::longValue);

        return monoToCompletable(delete.then(deleteScopes).as(trx::transactional));
    }
}
