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
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcDomain;
import io.gravitee.am.repository.jdbc.management.api.spring.domain.SpringDomainIdentitiesRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.domain.SpringDomainRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.domain.SpringDomainTagRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.domain.SpringDomainVHostsRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

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
    public Single<Set<Domain>> findAll() {
        LOGGER.debug("findAll()");
        Flowable<Domain> domains = domainRepository.findAll().map(this::toDomain);
        return domains.flatMap(this::completeDomain)
                .doOnError((error) -> LOGGER.error("unable to retrieve all domain", error))
                .toList().map(HashSet::new);
    }

    @Override
    public Flowable<Domain> findAllByCriteria(DomainCriteria criteria) {

        Criteria whereClause = Criteria.empty();
        Criteria alertEnableClause = Criteria.empty();

        if (criteria.isAlertEnabled().isPresent()) {
            alertEnableClause = where("alert_enabled").is(criteria.isAlertEnabled().get());
        }

        whereClause = whereClause.and(alertEnableClause);

        return fluxToFlowable(dbClient.select()
                .from(JdbcDomain.class)
                .matching(from(whereClause))
                .as(JdbcDomain.class)
                .all())
                .map(this::toDomain)
                .flatMap(this::completeDomain)
                .doOnError(error -> LOGGER.error("Unable to retrieve Domain with criteria {}", criteria, error));
    }

    @Override
    public Single<Set<Domain>> findByIdIn(Collection<String> ids) {
        LOGGER.debug("findByIdIn({})", ids);
        if (ids == null || ids.isEmpty()) {
            return Single.just(Collections.emptySet());
        }
        Flowable<Domain> domains = domainRepository.findAllById(ids).map(this::toDomain);
        return domains.flatMap(this::completeDomain)
                .doOnError((error) -> LOGGER.error("unable to retrieve all domain by ids {}", ids, error))
                .toList().map(HashSet::new);
    }

    @Override
    public Flowable<Domain> findAllByReferenceId(String environmentId) {
        LOGGER.debug("findAllByReferenceId({})", environmentId);
        Flowable<Domain> domains = domainRepository.findAllByReferenceId(environmentId, ReferenceType.ENVIRONMENT.name()).map(this::toDomain);
        return domains.flatMap(this::completeDomain)
                .doOnError((error) -> LOGGER.error("unable to retrieve all domain with environment {}", environmentId, error));
    }

    @Override
    public Maybe<Domain> findById(String id) {
        LOGGER.debug("findById({})", id);
        Flowable<Domain> domains = domainRepository.findById(id).map(this::toDomain).toFlowable();
        return domains.flatMap(this::completeDomain).doOnError((error) -> LOGGER.error("unable to retrieve domain with id {}", id, error)).firstElement();
    }

    @Override
    public Maybe<Domain> findByHrid(ReferenceType referenceType, String referenceId, String hrid) {
        LOGGER.debug("findByHrid({})", hrid);
        Flowable<Domain> domains = domainRepository.findByHrid(referenceId, referenceType.name(), hrid).map(this::toDomain).toFlowable();
        return domains.flatMap(this::completeDomain).doOnError((error) -> LOGGER.error("unable to retrieve domain with hrid {}", hrid, error)).firstElement();
    }

    @Override
    public Single<Domain> create(Domain item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create Domain with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> insertAction = dbClient.insert()
                .into(JdbcDomain.class)
                .using(toJdbcDomain(item))
                .fetch().rowsUpdated();
        insertAction = persistChildEntities(insertAction, item);

        return monoToSingle(insertAction
                .as(trx::transactional)
                .then(maybeToMono(findById(item.getId()))))
                .doOnError((error) -> LOGGER.error("unable to create domain with id {}", item.getId(), error));
    }

    @Override
    public Single<Domain> update(Domain item) {
        LOGGER.debug("update Domain with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> updateAction = dbClient.update()
                .table(JdbcDomain.class)
                .using(toJdbcDomain(item))
                .fetch().rowsUpdated();

        updateAction = updateAction.then(deleteChildEntities(item.getId()));
        updateAction = persistChildEntities(updateAction, item);

        return monoToSingle(updateAction
                .as(trx::transactional)
                .then(maybeToMono(findById(item.getId()))))
                .doOnError((error) -> LOGGER.error("unable to update domain with id {}", item.getId(), error));
    }

    @Override
    public Completable delete(String domainId) {
        LOGGER.debug("delete Domain with id {}", domainId);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        return monoToCompletable(dbClient.delete()
                .from(JdbcDomain.class)
                .matching(from(where("id").is(domainId)))
                .fetch().rowsUpdated()
                .then(deleteChildEntities(domainId))
                .as(trx::transactional))
                .doOnError((error) -> LOGGER.error("unable to delete Domain with id {}", domainId, error));
    }

    @Override
    public Flowable<Domain> search(String environmentId, String query){
        LOGGER.debug("search({}, {})", environmentId, query);

        boolean wildcardMatch = query.contains("*");
        String wildcardQuery = query.replaceAll("\\*+", "%");

        String search = new StringBuilder("SELECT * FROM domains d WHERE")

                .append(" d.reference_type = :referenceType AND d.reference_id = :referenceId")
                .append(" AND upper(d.name) " + (wildcardMatch ? "LIKE" : "="))
                .append(" :value")
                .toString();

        return fluxToFlowable(dbClient.execute(search)
                .bind("referenceType", ReferenceType.ENVIRONMENT.name())
                .bind("referenceId", environmentId)
                .bind("value", wildcardMatch ? wildcardQuery.toUpperCase() : query.toUpperCase())
                .as(JdbcDomain.class)
                .fetch()
                .all())
                .map(this::toDomain)
                .flatMap(this::completeDomain)
                .doOnError((error) -> LOGGER.error("Unable to search domains with referenceId {}", environmentId, error));
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

    private Mono<Integer> persistChildEntities(Mono<Integer> actionFlow, Domain item) {
        final Set<String> identities = item.getIdentities();
        if (identities != null && !identities.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(identities).concatMap(idp -> {
                JdbcDomain.Identity identity = new JdbcDomain.Identity();
                identity.setIdentity(idp);
                identity.setDomainId(item.getId());
                return dbClient.insert().into(JdbcDomain.Identity.class).using(identity).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        final Set<String> tags = item.getTags();
        if (tags != null && !tags.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(tags).concatMap(tagValue -> {
                JdbcDomain.Tag tag = new JdbcDomain.Tag();
                tag.setTag(tagValue);
                tag.setDomainId(item.getId());
                return dbClient.insert().into(JdbcDomain.Tag.class).using(tag).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        final List<VirtualHost> virtualHosts = item.getVhosts();
        if (virtualHosts != null && !virtualHosts.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromStream(virtualHosts.stream().map(this::toJdbcVHost)).concatMap(jdbcVHost -> {
                jdbcVHost.setDomainId(item.getId());
                return dbClient.insert().into(JdbcDomain.Vhost.class).using(jdbcVHost).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        return actionFlow;
    }

    private Mono<Integer> deleteChildEntities(String domainId) {
        Mono<Integer> deleteVirtualHosts = dbClient.delete().from(JdbcDomain.Vhost.class).matching(from(where("domain_id").is(domainId))).fetch().rowsUpdated();
        Mono<Integer> deleteIdentities = dbClient.delete().from(JdbcDomain.Identity.class).matching(from(where("domain_id").is(domainId))).fetch().rowsUpdated();
        Mono<Integer> deleteTags = dbClient.delete().from(JdbcDomain.Tag.class).matching(from(where("domain_id").is(domainId))).fetch().rowsUpdated();
        return deleteVirtualHosts.then(deleteIdentities).then(deleteTags);
    }
}
