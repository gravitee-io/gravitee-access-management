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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

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
    public Flowable<Factor> findAll() {
        LOGGER.debug("findAll()");
        return factorRepository.findAll()
                .map(this::toEntity);
    }

    @Override
    public Flowable<Factor> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return factorRepository.findByDomain(domain)
                .map(this::toEntity);
    }

    @Override
    public Maybe<Factor> findById(String id) {
        LOGGER.debug("findById({})", id);
        return factorRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<Factor> create(Factor item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create factor with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<Factor> update(Factor item) {
        LOGGER.debug("update factor with id {}", item.getId());
        return this.factorRepository.save(toJdbcEntity(item))
                .map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return factorRepository.deleteById(id);
    }
}
