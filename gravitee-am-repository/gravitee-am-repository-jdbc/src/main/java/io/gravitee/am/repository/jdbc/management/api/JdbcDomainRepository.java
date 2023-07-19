/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
        return domains.flatMap(this::completeDomain);
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
                .flatMap(this::completeDomain);
    }

    @Override
    public Flowable<Domain> findByIdIn(Collection<String> ids) {
        LOGGER.debug("findByIdIn({})", ids);
        if (ids == null || ids.isEmpty()) {
            return Flowable.empty();
        }
        Flowable<Domain> domains = domainRepository.findAllById(ids).map(this::toDomain);
        return domains.flatMap(this::completeDomain);
    }

    @Override
    public Flowable<Domain> findAllByReferenceId(String environmentId) {
        LOGGER.debug("findAllByReferenceId({})", environmentId);
        Flowable<Domain> domains = domainRepository.findAllByReferenceId(environmentId, ReferenceType.ENVIRONMENT.name()).map(this::toDomain);
        return domains.flatMap(this::completeDomain);
    }

    @Override
    public Maybe<Domain> findById(String id) {
        LOGGER.debug("findById({})", id);
        Flowable<Domain> domains = domainRepository.findById(id).map(this::toDomain).toFlowable();
        return domains.flatMap(this::completeDomain).firstElement();
    }

    @Override
    public Maybe<Domain> findByHrid(ReferenceType referenceType, String referenceId, String hrid) {
        LOGGER.debug("findByHrid({})", hrid);
        Flowable<Domain> domains = domainRepository.findByHrid(referenceId, referenceType.name(), hrid).map(this::toDomain).toFlowable();
        return domains.flatMap(this::completeDomain).firstElement();
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
                .then(maybeToMono(findById(item.getId()))));
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
                .then(maybeToMono(findById(item.getId()))));
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
                .doOnError((error) -> LOGGER.error("unable to delete Domain with id {}", domainId, error));
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
                .flatMap(this::completeDomain);
    }

    private Flowable<Domain> completeDomain(Domain entity) {
        return Flowable.just(entity).flatMap(domain ->
                identitiesRepository.findAllByDomainId(domain.getId()).map(JdbcDomain.Identity::getIdentity).toList().toFlowable().map(idps -> {
                    domain.setIdentities(new HashSet<>(idps));
                    return domain;
                })
        ).flatMap(domain ->
                tagRepository.findAllByDomainId(domain.getId()).map(JdbcDomain.Tag::getTag).toList().toFlowable().map(tags -> {
                    domain.setTags(new HashSet<>(tags));
                    return domain;
                })
        ).flatMap(domain ->
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
                                    .bind("domain", item.getId())
                                    .bind("idp", idp)
                                    .fetch().rowsUpdated())
                    .reduce(Long::sum));
        }

        final Set<String> tags = item.getTags();
        if (tags != null && !tags.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(tags).concatMap(tagValue ->
                            getTemplate().getDatabaseClient().sql("INSERT INTO domain_tags(domain_id, tag) VALUES(:domain, :tag)")
                                    .bind("domain", item.getId())
                                    .bind("tag", tagValue)
                                    .fetch().rowsUpdated())
                    .reduce(Long::sum));
        }

        final List<VirtualHost> virtualHosts = item.getVhosts();
        if (virtualHosts != null && !virtualHosts.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromStream(virtualHosts.stream().map(this::toJdbcVHost)).concatMap(jdbcVHost -> {
                jdbcVHost.setDomainId(item.getId());
                DatabaseClient.GenericExecuteSpec insert = getTemplate().getDatabaseClient().sql("INSERT INTO domain_vhosts(domain_id, host, path, override_entrypoint) VALUES(:domain, :host, :path, :override)")
                        .bind("domain", item.getId());
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
        Mono<Integer> deleteVirtualHosts = getTemplate().delete(criteria, JdbcDomain.Vhost.class);
        Mono<Integer> deleteIdentities = getTemplate().delete(criteria, JdbcDomain.Identity.class);
        Mono<Integer> deleteTags = getTemplate().delete(criteria, JdbcDomain.Tag.class);
        return deleteVirtualHosts.then(deleteIdentities).then(deleteTags).map(Integer::longValue);
    }
}
