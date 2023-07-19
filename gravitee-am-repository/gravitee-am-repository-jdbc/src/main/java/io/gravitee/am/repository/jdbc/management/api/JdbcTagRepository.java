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
import io.gravitee.am.model.Tag;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcTag;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringTagRepository;
import io.gravitee.am.repository.management.api.TagRepository;
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
public class JdbcTagRepository extends AbstractJdbcRepository implements TagRepository {

    @Autowired
    private SpringTagRepository tagRepository;

    protected Tag toEntity(JdbcTag entity) {
        return mapper.map(entity, Tag.class);
    }

    protected JdbcTag toJdbcEntity(Tag entity) {
        return mapper.map(entity, JdbcTag.class);
    }

    @Override
    public Maybe<Tag> findById(String id, String organizationId) {
        LOGGER.debug("findById({}, {})", id, organizationId);
        return tagRepository.findById(id, organizationId)
                .map(this::toEntity);
    }

    @Override
    public Flowable<Tag> findAll(String organizationId) {
        LOGGER.debug("findAll({})", organizationId);
        return tagRepository.findByOrganization(organizationId)
                .map(this::toEntity);
    }

    @Override
    public Maybe<Tag> findById(String id) {
        LOGGER.debug("findById({})", id);
        return tagRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<Tag> create(Tag item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create tag with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<Tag> update(Tag item) {
        LOGGER.debug("Update tag with id {}", item.getId());
        return tagRepository.save(toJdbcEntity(item))
                .map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return tagRepository.deleteById(id);
    }
}
