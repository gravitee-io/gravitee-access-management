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
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcOrganization;
import io.gravitee.am.repository.jdbc.management.api.spring.organization.SpringOrganizationDomainRestrictionRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.organization.SpringOrganizationHridsRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.organization.SpringOrganizationIdentitiesRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.organization.SpringOrganizationRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class JdbcOrganizationRepository extends AbstractJdbcRepository implements OrganizationRepository {

    @Autowired
    private SpringOrganizationRepository organizationRepository;
    @Autowired
    private SpringOrganizationIdentitiesRepository identitiesRepository;
    @Autowired
    private SpringOrganizationDomainRestrictionRepository domainRestrictionRepository;
    @Autowired
    private SpringOrganizationHridsRepository hridsRepository;

    protected Organization toOrganization(JdbcOrganization entity) {
        return mapper.map(entity, Organization.class);
    }

    protected JdbcOrganization toJdbcOrganization(Organization entity) {
        return mapper.map(entity, JdbcOrganization.class);
    }

    @Override
    public Flowable<Organization> findByHrids(List<String> hrids) {
        LOGGER.debug("findByHrids({})", hrids);

        final Flowable<Organization> result = organizationRepository.findByHrids(hrids)
                .map(this::toOrganization);

        return result.doOnError((error) -> LOGGER.error("unable to retrieve organizations with hrids {}", hrids, error));
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

        Maybe<List<String>> hrids = hridsRepository.findAllByOrganizationId(organizationId)
                .map(JdbcOrganization.Hrid::getHrid)
                .toList()
                .toMaybe();

        return organizationRepository.findById(organizationId)
                .map(this::toOrganization)
                .zipWith(identities, (org, idp) -> {
                    LOGGER.debug("findById({}) fetch {} identities", organizationId, idp.size());
                    org.setIdentities(idp);
                    return org;
                }).zipWith(domains, (org, domain) -> {
                    LOGGER.debug("findById({}) fetch {} domainRestrictions", organizationId, domain.size());
                    org.setDomainRestrictions(domain);
                    return org;
                }).zipWith(hrids, (org, hrid) -> {
                    LOGGER.debug("findById({}) fetch {} hrids", organizationId, hrid.size());
                    org.setHrids(hrid);
                    return org;
                });
    }

    @Override
    public Single<Organization> create(Organization organization) {
        organization.setId(organization.getId() == null ? RandomString.generate() : organization.getId());
        LOGGER.debug("create organization with id {}", organization.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Void> insert = dbClient.insert()
                .into(JdbcOrganization.class)
                .using(toJdbcOrganization(organization))
                .then();

        final Mono<Void> storeIdentities = storeIdentities(organization, false);
        final Mono<Void> storeDomainRestrictions = storeDomainRestrictions(organization, false);
        final Mono<Void> storeHrids = storeHrids(organization, false);

        return monoToSingle(insert
                .then(storeIdentities)
                .then(storeDomainRestrictions)
                .then(storeHrids)
                .as(trx::transactional)
                .then(maybeToMono(findById(organization.getId()))));
    }

    @Override
    public Single<Organization> update(Organization organization) {
        LOGGER.debug("update organization with id {}", organization.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);

        // prepare the update for organization table
        Mono<Void> update = dbClient.update()
                .table(JdbcOrganization.class)
                .using(toJdbcOrganization(organization))
                .matching(from(where("id").is(organization.getId()))).then();

        final Mono<Void> storeIdentities = storeIdentities(organization, true);
        final Mono<Void> storeDomainRestrictions = storeDomainRestrictions(organization, true);
        final Mono<Void> storeHrids = storeHrids(organization, true);

        return monoToSingle(update
                .then(storeIdentities)
                .then(storeDomainRestrictions)
                .then(storeHrids)
                .as(trx::transactional)
                .then(maybeToMono(findById(organization.getId()))));
    }

    @Override
    public Completable delete(String organizationId) {
        LOGGER.debug("delete organization with id {}", organizationId);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Void> deleteIdentities = deleteIdentities(organizationId);
        Mono<Void> deleteDomainRestrictions = deleteDomainRestrictions(organizationId);
        Mono<Void> deleteHrids = deleteHrids(organizationId);
        Mono<Void> delete = dbClient.delete().from(JdbcOrganization.class).matching(from(where("id").is(organizationId))).then();

        return monoToCompletable(delete
                .then(deleteDomainRestrictions)
                .then(deleteIdentities)
                .then(deleteHrids)
                .as(trx::transactional));
    }

    private Mono<Void> storeIdentities(Organization organization, boolean deleteFirst) {

        Mono<Void> delete = Mono.empty();

        if (deleteFirst) {
            delete = deleteIdentities(organization.getId());
        }

        final List<String> identities = organization.getIdentities();
        if (identities != null && !identities.isEmpty()) {
            return delete.thenMany(Flux.fromIterable(identities)
                    .map(identity -> {
                        JdbcOrganization.Identity dbIdentity = new JdbcOrganization.Identity();
                        dbIdentity.setIdentity(identity);
                        dbIdentity.setOrganizationId(organization.getId());
                        return dbIdentity;
                    })
                    .concatMap(dbIdentity -> dbClient.insert().into(JdbcOrganization.Identity.class).using(dbIdentity).then()))
                    .ignoreElements();
        }

        return Mono.empty();
    }

    private Mono<Void> storeDomainRestrictions(Organization organization, boolean deleteFirst) {

        Mono<Void> delete = Mono.empty();

        if (deleteFirst) {
            delete = deleteDomainRestrictions(organization.getId());
        }

        final List<String> domainRestrictions = organization.getDomainRestrictions();
        if (domainRestrictions != null && !domainRestrictions.isEmpty()) {
            // concat flows to create domainRestrictions
            return delete.thenMany(Flux.fromIterable(domainRestrictions)
                    .map(domainRestriction -> {
                        JdbcOrganization.DomainRestriction dbDomainRestriction = new JdbcOrganization.DomainRestriction();
                        dbDomainRestriction.setDomainRestriction(domainRestriction);
                        dbDomainRestriction.setOrganizationId(organization.getId());
                        return dbDomainRestriction;
                    })
                    .concatMap(dbDomainRestriction -> dbClient.insert().into(JdbcOrganization.DomainRestriction.class).using(dbDomainRestriction).then()))
                    .ignoreElements();
        }

        return Mono.empty();
    }

    private Mono<Void> storeHrids(Organization organization, boolean deleteFirst) {

        Mono<Void> delete = Mono.empty();

        if (deleteFirst) {
            delete = deleteHrids(organization.getId());
        }

        final List<String> hrids = organization.getHrids();
        if (hrids != null && !hrids.isEmpty()) {
            final ArrayList<JdbcOrganization.Hrid> dbHrids = new ArrayList<>();
            for (int i = 0; i < hrids.size(); i++) {
                JdbcOrganization.Hrid hrid = new JdbcOrganization.Hrid();
                hrid.setOrganizationId(organization.getId());
                hrid.setHrid(hrids.get(i));
                hrid.setPos(i);
                dbHrids.add(hrid);
            }
            return delete.thenMany(Flux.fromIterable(dbHrids)).
                    concatMap(hrid -> dbClient.insert().into(JdbcOrganization.Hrid.class).using(hrid).then())
                    .ignoreElements();
        }

        return Mono.empty();
    }

    private Mono<Void> deleteIdentities(String organizationId) {
        return dbClient.delete().from(JdbcOrganization.Identity.class).matching(from(where("organization_id").is(organizationId))).then();
    }

    private Mono<Void> deleteDomainRestrictions(String organizationId) {
        return dbClient.delete().from(JdbcOrganization.DomainRestriction.class).matching(from(where("organization_id").is(organizationId))).then();
    }

    private Mono<Void> deleteHrids(String organizationId) {
        return dbClient.delete().from(JdbcOrganization.Hrid.class).matching(from(where("organization_id").is(organizationId))).then();
    }
}
