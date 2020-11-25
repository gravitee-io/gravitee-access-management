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
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcEnvironment;
import io.gravitee.am.repository.jdbc.management.api.spring.environment.SpringEnvironmentDomainRestrictionRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.environment.SpringEnvironmentRepository;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
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
@Repository
public class JdbcEnvironmentRepository extends AbstractJdbcRepository implements EnvironmentRepository {

    @Autowired
    private SpringEnvironmentRepository environmentRepository;
    @Autowired
    private SpringEnvironmentDomainRestrictionRepository domainRestrictionRepository;

    protected Environment toEnvironment(JdbcEnvironment entity) {
        return mapper.map(entity, Environment.class);
    }

    protected JdbcEnvironment toJdbcEnvironment(Environment entity) {
        return mapper.map(entity, JdbcEnvironment.class);
    }

    @Override
    public Maybe<Environment> findById(String id, String organizationId) {
        LOGGER.debug("findById({},{})", id, organizationId);

        Maybe<List<String>> domains = domainRestrictionRepository.findAllByEnvironmentId(id)
                .map(JdbcEnvironment.DomainRestriction::getDomainRestriction)
                .toList()
                .toMaybe();

        Maybe<Environment> result = environmentRepository.findByIdAndOrganization(id, organizationId)
                .map(this::toEnvironment)
                .zipWith(domains, (org, dom) -> {
                    LOGGER.debug("findById({}, {}) fetch {} domainRestrictions", id, organizationId, dom == null ? 0 : dom.size());
                    org.setDomainRestrictions(dom);
                    return org;
                });

        return result.doOnError((error) -> LOGGER.error("unable to retrieve Environment with id {}", id, error));
    }

    @Override
    public Single<Long> count() {
        return this.environmentRepository.count();
    }

    @Override
    public Maybe<Environment> findById(String id) {
        LOGGER.debug("findById({})", id);

        Maybe<List<String>> domains = domainRestrictionRepository.findAllByEnvironmentId(id)
                .map(JdbcEnvironment.DomainRestriction::getDomainRestriction)
                .toList()
                .toMaybe();

        Maybe<Environment> result = environmentRepository.findById(id)
                .map(this::toEnvironment)
                .zipWith(domains, (org, dom) -> {
                    LOGGER.debug("findById({}) fetch {} domainRestrictions", id, dom == null ? 0 : dom.size());
                    org.setDomainRestrictions(dom);
                    return org;
                });

        return result.doOnError((error) -> LOGGER.error("unable to retrieve Environment with id {}", id, error));
    }

    @Override
    public Single<Environment> create(Environment item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create Environment with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> insertResult = dbClient.insert()
                .into(JdbcEnvironment.class)
                .using(toJdbcEnvironment(item))
                .fetch().rowsUpdated();

        final List<String> domainRestrictions = item.getDomainRestrictions();
        if (domainRestrictions != null && !domainRestrictions.isEmpty()) {
            insertResult = insertResult.then(Flux.fromIterable(domainRestrictions).concatMap(domain -> {
                JdbcEnvironment.DomainRestriction domainRestriction = new JdbcEnvironment.DomainRestriction();
                domainRestriction.setDomainRestriction(domain);
                domainRestriction.setEnvironmentId(item.getId());
                return dbClient.insert().into(JdbcEnvironment.DomainRestriction.class).using(domainRestriction).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        return monoToSingle(insertResult.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create Environment with id {}", item.getId(), error));
    }

    @Override
    public Single<Environment> update(Environment item) {
        LOGGER.debug("update environment with id {}", item.getId());
        Maybe<JdbcEnvironment> existEnv = environmentRepository.findById(item.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);
        return existEnv.toSingle()
                .flatMap((env) -> monoToSingle(updateEnvironmentFlow(item).as(trx::transactional)))
                .flatMap((i) -> this.findById(item.getId(), item.getOrganizationId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to update Environment with id {}", item.getId(), error));
    }

    private Mono<Integer> updateEnvironmentFlow(Environment item) {
        // prepare the update for environment table
        Mono<Integer> updateEnv = dbClient.update()
                .table(JdbcEnvironment.class)
                .using(toJdbcEnvironment(item))
                .matching(from(where("id").is(item.getId())
                        .and(where("organization_id").is(item.getOrganizationId()))))
                .fetch().rowsUpdated();

        // prepare the clean up of domainRestriction table
        Mono<Integer> deleteDomain = dbClient.delete().from(JdbcEnvironment.DomainRestriction.class)
                .matching(from(where("environment_id").is(item.getId()))).fetch().rowsUpdated();

        // concat flows
        updateEnv = updateEnv.then(deleteDomain);

        final List<String> domainRestrictions = item.getDomainRestrictions();
        if (domainRestrictions != null && !domainRestrictions.isEmpty()) {
            // concat flows to create domainRestrictions
            updateEnv = updateEnv.then(Flux.fromIterable(domainRestrictions).concatMap(domain -> {
                JdbcEnvironment.DomainRestriction domainRestriction = new JdbcEnvironment.DomainRestriction();
                domainRestriction.setDomainRestriction(domain);
                domainRestriction.setEnvironmentId(item.getId());
                return dbClient.insert().into(JdbcEnvironment.DomainRestriction.class).using(domainRestriction).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        return updateEnv;
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete environment with id {}", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> deleteDomain = dbClient.delete().from(JdbcEnvironment.DomainRestriction.class).matching(from(where("environment_id").is(id))).fetch().rowsUpdated();
        Mono<Integer> delete = dbClient.delete().from(JdbcEnvironment.class).matching(from(where("id").is(id))).fetch().rowsUpdated();
        return monoToCompletable(delete.then(deleteDomain).as(trx::transactional))
                .doOnError((error) -> LOGGER.error("unable to delete Environment with id {}", id, error));
    }
}
