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
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringProtectedResourceRepository;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

@Repository
public class JdbcProtectedResourceRepository extends AbstractJdbcRepository implements ProtectedResourceRepository {

    @Autowired
    private SpringProtectedResourceRepository spring;

    protected ProtectedResource toEntity(JdbcProtectedResource entity) {
        return mapper.map(entity, ProtectedResource.class);
    }

    protected JdbcProtectedResource toJdbcEntity(ProtectedResource entity) {
        return mapper.map(entity, JdbcProtectedResource.class);
    }

    @Override
    public Maybe<ProtectedResource> findById(String id) {
        return Maybe.empty(); // TODO AM-5762
    }

    @Override
    public Single<ProtectedResource> create(ProtectedResource item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create ProtectedResource with id {}", item.getId());

        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<ProtectedResource> update(ProtectedResource item) {
        return Single.just(item); // TODO AM-5756
    }

    @Override
    public Completable delete(String s) {
        return Completable.complete(); // TODO AM-5757
    }


    @Override
    public Maybe<ProtectedResource> findByDomainAndClient(String domainId, String clientId) {
        return null;
    }
}
