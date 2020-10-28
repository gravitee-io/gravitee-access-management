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
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcOrganization;
import io.gravitee.am.repository.jdbc.management.api.spring.organization.SpringOrganizationDomainRestrictionRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.organization.SpringOrganizationIdentitiesRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.organization.SpringOrganizationRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class JdbcOrganisationRepository extends AbstractJdbcRepository implements OrganizationRepository {

    @Autowired
    private SpringOrganizationRepository organizationRepository;
    @Autowired
    private SpringOrganizationIdentitiesRepository identitiesRepository;
    @Autowired
    private SpringOrganizationDomainRestrictionRepository domainRestrictionRepository;

    protected Organization toOrganization(JdbcOrganization entity) {
        return mapper.map(entity, Organization.class);
    }

    protected JdbcOrganization toJdbcOrganization(Organization entity) {
        return mapper.map(entity, JdbcOrganization.class);
    }

    @Override
    public Single<Long> count() {
        return organizationRepository.count();
    }

    @Override
    public Maybe<Organization> findById(String organizationId) {
        LOGGER.debug("findById({})", organizationId);

        Maybe<List<String>> identities = identitiesRepository.findAllByOrganizationId(organizationId)
                .map(JdbcOrganization.Identity::getIdentity)
                .toList()
                .toMaybe();

        Maybe<List<String>> domains = domainRestrictionRepository.findAllByOrganizationId(organizationId)
                .map(JdbcOrganization.DomainRestriction::getDomainRestriction)
                .toList()
                .toMaybe();

        return organizationRepository.findById(organizationId)
                .map(this::toOrganization)
                .zipWith(identities, (org, idp) -> {
                    LOGGER.debug("findById({}) fetch {} identities", organizationId, idp == null ? 0 : idp.size());
                    org.setIdentities(idp);
                    return org;
                }).zipWith(domains, (org, domain) -> {
                    LOGGER.debug("findById({}) fetch {} domainRestrictions", organizationId, domain == null ? 0 : domain.size());
                    org.setDomainRestrictions(domain);
                    return org;
                }).doOnError((error) -> LOGGER.error("unable to retrieve Organization with id {}", organizationId, error));
    }

    @Override
    public Single<Organization> create(Organization item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create organization with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> insertResult = dbClient.insert()
                .into(JdbcOrganization.class)
                .using(toJdbcOrganization(item))
                .fetch().rowsUpdated();

        final List<String> identities = item.getIdentities();
        if (identities != null && !identities.isEmpty()) {
          insertResult = insertResult.then(Flux.fromIterable(identities).concatMap(idp -> {
                JdbcOrganization.Identity identity = new JdbcOrganization.Identity();
                identity.setIdentity(idp);
                identity.setOrganizationId(item.getId());
                return dbClient.insert().into(JdbcOrganization.Identity.class).using(identity).fetch().rowsUpdated();
            }).reduce(Integer::sum));

        }

        final List<String> domainRestrictions = item.getDomainRestrictions();
        if (domainRestrictions != null && !domainRestrictions.isEmpty()) {
            insertResult = insertResult.then(Flux.fromIterable(domainRestrictions).concatMap(domain -> {
                JdbcOrganization.DomainRestriction domainRestriction = new JdbcOrganization.DomainRestriction();
                domainRestriction.setDomainRestriction(domain);
                domainRestriction.setOrganizationId(item.getId());
                return dbClient.insert().into(JdbcOrganization.DomainRestriction.class).using(domainRestriction).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        return monoToSingle(insertResult.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create Organization with id {}", item.getId(), error));
    }

    @Override
    public Single<Organization> update(Organization item) {
        LOGGER.debug("update organization with id {}", item.getId());
        Maybe<JdbcOrganization> existingOrga = organizationRepository.findById(item.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);
        return existingOrga.toSingle()
                .flatMap((readOrga) -> monoToSingle(updateOrganizationFlow(item).as(trx::transactional)))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to update Organization with id {}", item.getId(), error));
    }

    private Mono<Integer> updateOrganizationFlow(Organization item) {
        // prepare the update for organization table
        Mono<Integer> updateOrga = dbClient.update()
                .table(JdbcOrganization.class)
                .using(toJdbcOrganization(item))
                .matching(from(where("id").is(item.getId())))
                .fetch().rowsUpdated();

        // prepare the clean up of identities and domainRestriction table
        Mono<Integer> deleteIdp = dbClient.delete().from(JdbcOrganization.Identity.class)
                .matching(from(where("organization_id").is(item.getId()))).fetch().rowsUpdated();
        Mono<Integer> deleteDomain = dbClient.delete().from(JdbcOrganization.DomainRestriction.class)
                .matching(from(where("organization_id").is(item.getId()))).fetch().rowsUpdated();

        // concat flows
        updateOrga = updateOrga.then(deleteIdp).then(deleteDomain);

        final List<String> identities = item.getIdentities();
        if (identities != null && !identities.isEmpty()) {
            // concat flows to create identities
            updateOrga = updateOrga.then(Flux.fromIterable(identities).concatMap(idp -> {
                JdbcOrganization.Identity identity = new JdbcOrganization.Identity();
                identity.setIdentity(idp);
                identity.setOrganizationId(item.getId());
                return dbClient.insert().into(JdbcOrganization.Identity.class).using(identity).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        final List<String> domainRestrictions = item.getDomainRestrictions();
        if (domainRestrictions != null && !domainRestrictions.isEmpty()) {
            // concat flows to create domainRestrictions
            updateOrga = updateOrga.then(Flux.fromIterable(domainRestrictions).concatMap(domain -> {
                JdbcOrganization.DomainRestriction domainRestriction = new JdbcOrganization.DomainRestriction();
                domainRestriction.setDomainRestriction(domain);
                domainRestriction.setOrganizationId(item.getId());
                return dbClient.insert().into(JdbcOrganization.DomainRestriction.class).using(domainRestriction).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        return updateOrga;
    }

    @Override
    public Completable delete(String organizationId) {
        LOGGER.debug("delete organization with id {}", organizationId);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> deleteIdp = dbClient.delete().from(JdbcOrganization.Identity.class).matching(from(where("organization_id").is(organizationId))).fetch().rowsUpdated();
        Mono<Integer> deleteDomain = dbClient.delete().from(JdbcOrganization.DomainRestriction.class).matching(from(where("organization_id").is(organizationId))).fetch().rowsUpdated();
        Mono<Integer> delete = dbClient.delete().from(JdbcOrganization.class).matching(from(where("id").is(organizationId))).fetch().rowsUpdated();
        return monoToCompletable(delete
                .then(deleteDomain)
                .then(deleteIdp)
                .as(trx::transactional))
                .doOnError((error) -> LOGGER.error("unable to delete Organization with id {}", organizationId, error));
    }
}
