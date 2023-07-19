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
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcExtensionGrant;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringExtensionGrantRepository;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcExtensionGrantRepository extends AbstractJdbcRepository implements ExtensionGrantRepository {

    @Autowired
    private SpringExtensionGrantRepository extensionGrantRepository;

    protected ExtensionGrant toEntity(JdbcExtensionGrant entity) {
        return mapper.map(entity, ExtensionGrant.class);
    }

    protected JdbcExtensionGrant toJdbcEntity(ExtensionGrant entity) {
        return mapper.map(entity, JdbcExtensionGrant.class);
    }

    @Override
    public Flowable<ExtensionGrant> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return extensionGrantRepository.findByDomain(domain)
                .map(this::toEntity);
    }

    @Override
    public Maybe<ExtensionGrant> findByDomainAndName(String domain, String name) {
        LOGGER.debug("findByDomainAndName({}, {})", domain, name);
        return extensionGrantRepository.findByDomainAndName(domain, name)
                .map(this::toEntity);
    }

    @Override
    public Maybe<ExtensionGrant> findById(String id) {
        LOGGER.debug("findByDomainAndName({}, {})", id);
        return extensionGrantRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<ExtensionGrant> create(ExtensionGrant item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create extension grants  with id {}", item.getId());

        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<ExtensionGrant> update(ExtensionGrant item) {
        LOGGER.debug("update extension grants  with id {}", item.getId());
        return this.extensionGrantRepository.save(toJdbcEntity(item))
                .map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return extensionGrantRepository.deleteById(id);
    }
}
