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
import io.gravitee.am.repository.jdbc.management.api.model.JdbcEnvironment;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcOrganization;
import io.gravitee.am.repository.jdbc.management.api.spring.environment.SpringEnvironmentDomainRestrictionRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.environment.SpringEnvironmentHridsRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.environment.SpringEnvironmentRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.organization.SpringOrganizationHridsRepository;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
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
@Repository
public class JdbcEnvironmentRepository extends AbstractJdbcRepository implements EnvironmentRepository {

    @Autowired
    private SpringEnvironmentRepository environmentRepository;
    @Autowired
    private SpringEnvironmentDomainRestrictionRepository domainRestrictionRepository;
    @Autowired
    private SpringEnvironmentHridsRepository hridsRepository;

    protected Environment toEnvironment(JdbcEnvironment entity) {
        return mapper.map(entity, Environment.class);
    }

    protected JdbcEnvironment toJdbcEnvironment(Environment entity) {
        return mapper.map(entity, JdbcEnvironment.class);
    }

    @Override
    public Maybe<Environment> findById(String id, String organizationId) {
        LOGGER.debug("findById({},{})", id, organizationId);

        return environmentRepository.findByIdAndOrganization(id, organizationId)
                .map(this::toEnvironment)
                .flatMap(environment -> retrieveDomainRestrictions(environment).toMaybe())
                .flatMap(environment -> retrieveHrids(environment).toMaybe());
    }

    @Override
    public Flowable<Environment> findAll(String organizationId) {
        LOGGER.debug("findAll({})", organizationId);

        final Flowable<Environment> result = environmentRepository.findByOrganization(organizationId)
                .map(this::toEnvironment)
                .flatMapSingle(this::retrieveDomainRestrictions)
                .flatMapSingle(this::retrieveHrids);

        return result.doOnError((error) -> LOGGER.error("unable to retrieve Environments with organizationId {}", organizationId, error));
    }

    @Override
    public Single<Long> count() {
        return this.environmentRepository.count();
    }

    @Override
    public Maybe<Environment> findById(String id) {
        LOGGER.debug("findById({})", id);

        Maybe<Environment> result = environmentRepository.findById(id)
                .map(this::toEnvironment)
                .flatMap(environment -> retrieveDomainRestrictions(environment).toMaybe())
                .flatMap(environment -> retrieveHrids(environment).toMaybe());

        return result.doOnError((error) -> LOGGER.error("unable to retrieve Environment with id {}", id, error));
    }

    @Override
    public Single<Environment> create(Environment environment) {
        environment.setId(environment.getId() == null ? RandomString.generate() : environment.getId());
        LOGGER.debug("create Environment with id {}", environment.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Void> insert = dbClient.insert()
                .into(JdbcEnvironment.class)
                .using(toJdbcEnvironment(environment))
                .then();

        final Mono<Void> storeDomainRestrictions = storeDomainRestrictions(environment, false);
        final Mono<Void> storeHrids = storeHrids(environment, false);

        return monoToSingle(insert
                .then(storeDomainRestrictions)
                .then(storeHrids)
                .as(trx::transactional)
                .then(maybeToMono(findById(environment.getId()))));
    }

    @Override
    public Single<Environment> update(Environment environment) {
        LOGGER.debug("update environment with id {}", environment.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);

        // prepare the update for environment table
        Mono<Void> update = dbClient.update()
                .table(JdbcEnvironment.class)
                .using(toJdbcEnvironment(environment))
                .matching(from(where("id").is(environment.getId()))).then();

        return monoToSingle(update
                .then(storeDomainRestrictions(environment, true))
                .then(storeHrids(environment, true))
                .as(trx::transactional)
                .then(maybeToMono(findById(environment.getId()))));
    }

    @Override
    public Completable delete(String environmentId) {
        LOGGER.debug("delete environment with id {}", environmentId);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Void> deleteDomainRestrictions = deleteDomainRestrictions(environmentId);
        Mono<Void> deleteHrids = deleteHrids(environmentId);
        Mono<Void> delete = dbClient.delete().from(JdbcEnvironment.class).matching(from(where("id").is(environmentId))).then();

        return monoToCompletable(delete
                .then(deleteDomainRestrictions)
                .then(deleteHrids)
                .as(trx::transactional));
    }

    private Single<Environment> retrieveDomainRestrictions(Environment environment) {
        return domainRestrictionRepository.findAllByEnvironmentId(environment.getId())
                .map(JdbcEnvironment.DomainRestriction::getDomainRestriction)
                .toList()
                .doOnSuccess(domainRestrictions -> LOGGER.debug("findById({}) fetch {} domainRestrictions", environment.getId(), domainRestrictions.size()))
                .doOnSuccess(environment::setDomainRestrictions)
                .map(domainRestriction -> environment);
    }

    private Single<Environment> retrieveHrids(Environment environment) {
        return hridsRepository.findAllByEnvironmentId(environment.getId())
                .map(JdbcEnvironment.Hrid::getHrid)
                .toList()
                .doOnSuccess(hrids -> LOGGER.debug("findById({}) fetch {} hrids", environment.getId(), hrids.size()))
                .doOnSuccess(environment::setHrids)
                .map(hrids -> environment);
    }

    private Mono<Void> storeDomainRestrictions(Environment environment, boolean deleteFirst) {

        Mono<Void> delete = Mono.empty();

        if (deleteFirst) {
            delete = deleteDomainRestrictions(environment.getId());
        }

        final List<String> domainRestrictions = environment.getDomainRestrictions();
        if (domainRestrictions != null && !domainRestrictions.isEmpty()) {
            // concat flows to create domainRestrictions
            return delete.thenMany(Flux.fromIterable(domainRestrictions)
                    .map(domainRestriction -> {
                        JdbcEnvironment.DomainRestriction dbDomainRestriction = new JdbcEnvironment.DomainRestriction();
                        dbDomainRestriction.setDomainRestriction(domainRestriction);
                        dbDomainRestriction.setEnvironmentId(environment.getId());
                        return dbDomainRestriction;
                    })
                    .concatMap(dbDomainRestriction -> dbClient.insert().into(JdbcEnvironment.DomainRestriction.class).using(dbDomainRestriction).then()))
                    .ignoreElements();
        }

        return Mono.empty();
    }

    private Mono<Void> storeHrids(Environment environment, boolean deleteFirst) {

        Mono<Void> delete = Mono.empty();

        if (deleteFirst) {
            delete = deleteHrids(environment.getId());
        }

        final List<String> hrids = environment.getHrids();
        if (hrids != null && !hrids.isEmpty()) {
            final ArrayList<JdbcEnvironment.Hrid> dbHrids = new ArrayList<>();
            for (int i = 0; i < hrids.size(); i++) {
                JdbcEnvironment.Hrid hrid = new JdbcEnvironment.Hrid();
                hrid.setEnvironmentId(environment.getId());
                hrid.setHrid(hrids.get(i));
                hrid.setPos(i);
                dbHrids.add(hrid);
            }
            return delete.thenMany(Flux.fromIterable(dbHrids)).
                    concatMap(hrid -> dbClient.insert().into(JdbcEnvironment.Hrid.class).using(hrid).then())
                    .ignoreElements();
        }

        return Mono.empty();
    }

    private Mono<Void> deleteDomainRestrictions(String environmentId) {
        return dbClient.delete().from(JdbcEnvironment.DomainRestriction.class).matching(from(where("environment_id").is(environmentId))).then();
    }

    private Mono<Void> deleteHrids(String environmentId) {
        return dbClient.delete().from(JdbcEnvironment.Hrid.class).matching(from(where("environment_id").is(environmentId))).then();
    }
}
