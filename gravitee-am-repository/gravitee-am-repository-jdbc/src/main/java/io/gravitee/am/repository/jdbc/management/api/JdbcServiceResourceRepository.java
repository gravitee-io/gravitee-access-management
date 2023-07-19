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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcServiceResource;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringServiceResourceRepository;
import io.gravitee.am.repository.management.api.ServiceResourceRepository;
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
public class JdbcServiceResourceRepository extends AbstractJdbcRepository implements ServiceResourceRepository {

    @Autowired
    protected SpringServiceResourceRepository serviceResourceRepository;

    protected ServiceResource toEntity(JdbcServiceResource entity) {
        return mapper.map(entity, ServiceResource.class);
    }

    protected JdbcServiceResource toJdbcEntity(ServiceResource entity) {
        return mapper.map(entity, JdbcServiceResource.class);
    }

    @Override
    public Maybe<ServiceResource> findById(String id) {
        LOGGER.debug("findById({})", id);
        return serviceResourceRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<ServiceResource> create(ServiceResource item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Reporter with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<ServiceResource> update(ServiceResource item) {
        LOGGER.debug("Update resource with id '{}'", item.getId());
        return serviceResourceRepository.save(toJdbcEntity(item))
                .map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return serviceResourceRepository.deleteById(id);
    }

    @Override
    public Flowable<ServiceResource> findByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({}, {})", referenceType, referenceId);
        return serviceResourceRepository.findByReference(referenceType.name(), referenceId)
                .map(this::toEntity);
    }
}
