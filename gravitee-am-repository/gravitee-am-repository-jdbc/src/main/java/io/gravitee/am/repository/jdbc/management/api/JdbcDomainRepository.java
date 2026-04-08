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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.model.common.CursorPage;
import io.gravitee.am.model.common.CursorRequest;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcDomain;
import io.gravitee.am.repository.jdbc.management.api.spring.domain.SpringDomainIdentitiesRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.domain.SpringDomainRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.domain.SpringDomainTagRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.domain.SpringDomainVHostsRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.maybeToMono;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcDomainRepository extends AbstractJdbcRepository implements DomainRepository {

    public static final String DOMAIN = "domain";
    @Autowired
    private SpringDomainRepository domainRepository;
    @Autowired
    private SpringDomainIdentitiesRepository identitiesRepository;
    @Autowired
    private SpringDomainTagRepository tagRepository;
    @Autowired
    private SpringDomainVHostsRepository vHostsRepository;

    protected Domain toDomain(JdbcDomain entity) {
        return mapper.map(entity, Domain.class);
    }

    protected JdbcDomain toJdbcDomain(Domain entity) {
        return mapper.map(entity, JdbcDomain.class);
    }

    protected VirtualHost toVirtualHost(JdbcDomain.Vhost vhost) {
        return mapper.map(vhost, VirtualHost.class);
    }

    protected JdbcDomain.Vhost toJdbcVHost(VirtualHost entity) {
        return mapper.map(entity, JdbcDomain.Vhost.class);
    }

    @Override
    public Flowable<Domain> findAll() {
        LOGGER.debug("findAll()");
        Flowable<Domain> domains = domainRepository.findAll().map(this::toDomain);
        return domains.concatMap(this::completeDomain)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Domain> findAllByCriteria(DomainCriteria criteria) {

        Criteria whereClause = Criteria.empty();
        Criteria alertEnableClause = Criteria.empty();

        if (criteria.isAlertEnabled().isPresent()) {
            alertEnableClause = where("alert_enabled").is(criteria.isAlertEnabled().get());
        }

        whereClause = whereClause.and(alertEnableClause);

        return fluxToFlowable(getTemplate().select(JdbcDomain.class)
                .matching(Query.query(whereClause))
                .all())
                .map(this::toDomain)
                .concatMap(this::completeDomain)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Domain> findByIdIn(Collection<String> ids) {
        LOGGER.debug("findByIdIn({})", ids);
        if (ids == null || ids.isEmpty()) {
            return Flowable.empty();
        }
        Flowable<Domain> domains = domainRepository.findAllById(ids).map(this::toDomain);
        return domains.concatMap(this::completeDomain)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Domain> findAllByReferenceId(String environmentId) {
        LOGGER.debug("findAllByReferenceId({})", environmentId);
        Flowable<Domain> domains = domainRepository.findAllByReferenceId(environmentId, ReferenceType.ENVIRONMENT.name()).map(this::toDomain);
        return domains.concatMap(this::completeDomain)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Domain> findById(String id) {
        LOGGER.debug("findById({})", id);
        Flowable<Domain> domains = domainRepository.findById(id).map(this::toDomain).toFlowable();
        return domains.flatMap(this::completeDomain).firstElement()
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Domain> findByHrid(ReferenceType referenceType, String referenceId, String hrid) {
        LOGGER.debug("findByHrid({})", hrid);
        Flowable<Domain> domains = domainRepository.findByHrid(referenceId, referenceType.name(), hrid).map(this::toDomain).toFlowable();
        return domains.flatMap(this::completeDomain).firstElement()
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Domain> create(Domain item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create Domain with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> insertAction = getTemplate().insert(toJdbcDomain(item)).map(__ -> 1L); // TODO
        insertAction = persistChildEntities(insertAction, item);

        return monoToSingle(insertAction
                .as(trx::transactional)
                .then(maybeToMono(findById(item.getId()))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Domain> update(Domain item) {
        LOGGER.debug("update Domain with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> updateAction = getTemplate().update(toJdbcDomain(item)).map(__ -> 1L); // TODO

        updateAction = updateAction.then(deleteChildEntities(item.getId()));
        updateAction = persistChildEntities(updateAction, item);

        return monoToSingle(updateAction
                .as(trx::transactional)
                .then(maybeToMono(findById(item.getId()))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String domainId) {
        LOGGER.debug("delete Domain with id {}", domainId);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        return monoToCompletable(getTemplate().delete(JdbcDomain.class)
                .matching(Query.query(where("id").is(domainId)))
                .all()
                .then(deleteChildEntities(domainId))
                .as(trx::transactional))
                .doOnError(error -> LOGGER.error("unable to delete Domain with id {}", domainId, error))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Domain> search(String environmentId, String query) {
        LOGGER.debug("search({}, {})", environmentId, query);

        boolean wildcardMatch = query.contains("*");
        String wildcardQuery = query.replaceAll("\\*+", "%");

        String search = new StringBuilder("SELECT * FROM domains d WHERE")

                .append(" d.reference_type = :refType AND d.reference_id = :refId")
                .append(" AND UPPER(d.hrid) " + (wildcardMatch ? "LIKE" : "="))
                .append(" :value")
                .toString();

        return fluxToFlowable(getTemplate().getDatabaseClient().sql(search)
                .bind("refType", ReferenceType.ENVIRONMENT.name())
                .bind("refId", environmentId)
                .bind("value", wildcardMatch ? wildcardQuery.toUpperCase() : query.toUpperCase())
                .map((row, rowMetadata) -> rowMapper.read(JdbcDomain.class, row))
                .all())
                .map(this::toDomain)
                .concatMap(this::completeDomain)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CursorPage<Domain>> findByEnvironmentCursor(String environmentId, CursorRequest cursor) {
        return executeDomainCursorQuery(environmentId, null, cursor);
    }

    @Override
    public Single<CursorPage<Domain>> searchByEnvironmentCursor(String environmentId, String query, CursorRequest cursor) {
        return executeDomainCursorQuery(environmentId, query, cursor);
    }

    private Single<CursorPage<Domain>> executeDomainCursorQuery(String environmentId, String searchQuery, CursorRequest cursor) {
        boolean ascending = cursor.getDirection().isAscending();
        String compareOp = ascending ? ">" : "<";
        String sortDir = ascending ? "ASC" : "DESC";
        int fetchLimit = cursor.getLimit() + 1;

        var sql = new StringBuilder("SELECT * FROM domains d WHERE d.reference_type = :refType AND d.reference_id = :refId");

        if (searchQuery != null) {
            boolean wildcardMatch = searchQuery.contains("*");
            sql.append(" AND UPPER(d.hrid) ").append(wildcardMatch ? "LIKE" : "=").append(" :searchValue");
        }

        if (!cursor.isFirstPage()) {
            sql.append(" AND (LOWER(d.name) ").append(compareOp).append(" LOWER(:lastSort)")
               .append(" OR (LOWER(d.name) = LOWER(:lastSort) AND d.id ").append(compareOp).append(" :lastId))");
        }

        sql.append(" ORDER BY LOWER(d.name) ").append(sortDir).append(", d.id ").append(sortDir)
           .append(" LIMIT :fetchLimit"); // TODO LIMIT is not portable to MSSQL/Oracle — use dialect helper when available

        DatabaseClient.GenericExecuteSpec spec = getTemplate().getDatabaseClient().sql(sql.toString())
                .bind("refType", ReferenceType.ENVIRONMENT.name())
                .bind("refId", environmentId)
                .bind("fetchLimit", fetchLimit);

        if (searchQuery != null) {
            boolean wildcardMatch = searchQuery.contains("*");
            String value = wildcardMatch ? searchQuery.replaceAll("\\*+", "%").toUpperCase() : searchQuery.toUpperCase();
            spec = spec.bind("searchValue", value);
        }

        if (!cursor.isFirstPage()) {
            spec = spec.bind("lastSort", cursor.getLastSortValue())
                       .bind("lastId", cursor.getLastId());
        }

        // Count query uses the same base filter without keyset conditions
        var countSql = new StringBuilder("SELECT COUNT(*) FROM domains d WHERE d.reference_type = :refType AND d.reference_id = :refId");
        if (searchQuery != null) {
            boolean wildcardMatch2 = searchQuery.contains("*");
            countSql.append(" AND UPPER(d.hrid) ").append(wildcardMatch2 ? "LIKE" : "=").append(" :searchValue");
        }

        DatabaseClient.GenericExecuteSpec countSpec = getTemplate().getDatabaseClient().sql(countSql.toString())
                .bind("refType", ReferenceType.ENVIRONMENT.name())
                .bind("refId", environmentId);
        if (searchQuery != null) {
            boolean wildcardMatch2 = searchQuery.contains("*");
            String value2 = wildcardMatch2 ? searchQuery.replaceAll("\\*+", "%").toUpperCase() : searchQuery.toUpperCase();
            countSpec = countSpec.bind("searchValue", value2);
        }

        Single<Long> countSingle = monoToSingle(countSpec.map((row, rowMetadata) -> row.get(0, Long.class)).first());

        return fluxToFlowable(spec.map((row, rowMetadata) -> rowMapper.read(JdbcDomain.class, row)).all())
                .map(this::toDomain)
                .concatMap(this::completeDomain)
                .toList()
                .flatMap(items -> countSingle.map(totalCount -> buildCursorPage(items, cursor, Domain::getName, Domain::getId).withTotalCount(totalCount)))
                .observeOn(Schedulers.computation());
    }

    private <T> CursorPage<T> buildCursorPage(List<T> items, CursorRequest cursor,
                                                java.util.function.Function<T, String> sortValueExtractor,
                                                java.util.function.Function<T, String> idExtractor) {
        boolean hasNext = items.size() > cursor.getLimit();
        List<T> data = hasNext ? items.subList(0, cursor.getLimit()) : items;
        String nextCursor = null;
        if (hasNext && !data.isEmpty()) {
            T last = data.get(data.size() - 1);
            nextCursor = CursorRequest.encode(sortValueExtractor.apply(last), idExtractor.apply(last), cursor.getDirection(), cursor.getSortField());
        }
        return new CursorPage<>(data, nextCursor);
    }

    private Flowable<Domain> completeDomain(Domain entity) {
        return Flowable.just(entity).concatMap(domain ->
                identitiesRepository.findAllByDomainId(domain.getId()).map(JdbcDomain.Identity::getIdentity).toList().toFlowable().map(idps -> {
                    domain.setIdentities(new HashSet<>(idps));
                    return domain;
                })
        ).concatMap(domain ->
                tagRepository.findAllByDomainId(domain.getId()).map(JdbcDomain.Tag::getTag).toList().toFlowable().map(tags -> {
                    domain.setTags(new HashSet<>(tags));
                    return domain;
                })
        ).concatMap(domain ->
                vHostsRepository.findAllByDomainId(domain.getId()).map(this::toVirtualHost).toList().toFlowable().map(vhosts -> {
                    domain.setVhosts(vhosts);
                    return domain;
                })
        );
    }

    private Mono<Long> persistChildEntities(Mono<Long> actionFlow, Domain item) {
        final Set<String> identities = item.getIdentities();
        if (identities != null && !identities.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(identities).concatMap(idp ->
                            getTemplate().getDatabaseClient().sql("INSERT INTO domain_identities(domain_id, identity_id) VALUES(:domain, :idp)")
                                    .bind(DOMAIN, item.getId())
                                    .bind("idp", idp)
                                    .fetch().rowsUpdated())
                    .reduce(Long::sum));
        }

        final Set<String> tags = item.getTags();
        if (tags != null && !tags.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(tags).concatMap(tagValue ->
                            getTemplate().getDatabaseClient().sql("INSERT INTO domain_tags(domain_id, tag) VALUES(:domain, :tag)")
                                    .bind(DOMAIN, item.getId())
                                    .bind("tag", tagValue)
                                    .fetch().rowsUpdated())
                    .reduce(Long::sum));
        }

        final List<VirtualHost> virtualHosts = item.getVhosts();
        if (virtualHosts != null && !virtualHosts.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromStream(virtualHosts.stream().map(this::toJdbcVHost)).concatMap(jdbcVHost -> {
                jdbcVHost.setDomainId(item.getId());
                DatabaseClient.GenericExecuteSpec insert = getTemplate().getDatabaseClient().sql("INSERT INTO domain_vhosts(domain_id, host, path, override_entrypoint) VALUES(:domain, :host, :path, :override)")
                        .bind(DOMAIN, item.getId());
                insert = jdbcVHost.getHost() != null ? insert.bind("host", jdbcVHost.getHost()) : insert.bindNull("host", String.class);
                insert = jdbcVHost.getPath() != null ? insert.bind("path", jdbcVHost.getPath()) : insert.bindNull("path", String.class);
                return insert.bind("override", jdbcVHost.isOverrideEntrypoint())
                        .fetch().rowsUpdated();
            }).reduce(Long::sum));
        }

        return actionFlow;
    }

    private Mono<Long> deleteChildEntities(String domainId) {
        final Query criteria = Query.query(where("domain_id").is(domainId));
        Mono<Long> deleteVirtualHosts = getTemplate().delete(criteria, JdbcDomain.Vhost.class);
        Mono<Long> deleteIdentities = getTemplate().delete(criteria, JdbcDomain.Identity.class);
        Mono<Long> deleteTags = getTemplate().delete(criteria, JdbcDomain.Tag.class);
        return deleteVirtualHosts.then(deleteIdentities).then(deleteTags);
    }
}
