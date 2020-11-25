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
import io.gravitee.am.model.Factor;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcFactor;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringFactorRepository;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcFactorRepository extends AbstractJdbcRepository implements FactorRepository {

    @Autowired
    private SpringFactorRepository factorRepository;

    protected Factor toEntity(JdbcFactor entity) {
        return mapper.map(entity, Factor.class);
    }

    protected JdbcFactor toJdbcEntity(Factor entity) {
        return mapper.map(entity, JdbcFactor.class);
    }

    @Override
    public Single<Set<Factor>> findAll() {
        LOGGER.debug("findAll()");
        return factorRepository.findAll()
                .map(this::toEntity)
                .toList()
                .map(list -> {
            Set<Factor> set = new HashSet<>(list);
            return set;
        }).doOnError(error -> LOGGER.error("Unable to retrieve all factors", error));
    }

    @Override
    public Single<Set<Factor>> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return factorRepository.findByDomain(domain)
                .map(this::toEntity)
                .toList()
                .map(list -> {
            Set<Factor> set = new HashSet<>(list);
            return set;
        }).doOnError(error -> LOGGER.error("Unable to retrieve all factors with domain '{}'", domain , error));
    }

    @Override
    public Maybe<Factor> findByDomainAndFactorType(String domain, String factorType) {
        LOGGER.debug("findByDomainAndFactorType({}, {})", domain, factorType);
        return factorRepository.findByDomainAndFactorType(domain, factorType)
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve factor with domain '{}' and factorType '{}'", domain, factorType , error));
    }

    @Override
    public Maybe<Factor> findById(String id) {
        LOGGER.debug("findById({})", id);
        return factorRepository.findById(id)
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve factor with id '{}'", id , error));
    }

    @Override
    public Single<Factor> create(Factor item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create factor with id {}", item.getId());

        Mono<Integer> action = dbClient.insert()
                .into(JdbcFactor.class)
                .using(toJdbcEntity(item))
                .fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create factor with id {}", item.getId(), error));
    }

    @Override
    public Single<Factor> update(Factor item) {
        LOGGER.debug("update factor with id {}", item.getId());
        return this.factorRepository.save(toJdbcEntity(item))
                .map(this::toEntity)
                .doOnError((error) -> LOGGER.error("unable to update factor with id {}", item.getId(), error));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return factorRepository.deleteById(id)
                .doOnError(error -> LOGGER.error("Unable to delete factor with id '{}'", id , error));

    }
}
