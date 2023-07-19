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
import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcForm;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringFormRepository;
import io.gravitee.am.repository.management.api.FormRepository;
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
public class JdbcFormRepository extends AbstractJdbcRepository implements FormRepository {

    @Autowired
    private SpringFormRepository formRepository;

    protected Form toEntity(JdbcForm entity) {
        return mapper.map(entity, Form.class);
    }

    protected JdbcForm toJdbcEntity(Form entity) {
        return mapper.map(entity, JdbcForm.class);
    }

    @Override
    public Flowable<Form>  findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findAll({}, {})", referenceType, referenceId);
        return formRepository.findAll(referenceType.name(), referenceId)
                .map(this::toEntity);
    }

    @Override
    public Flowable<Form> findAll(ReferenceType referenceType) {LOGGER.debug("findAll({})", referenceType);
        return formRepository.findAll(referenceType.name())
                .map(this::toEntity);
    }

    @Override
    public Flowable<Form> findByClient(ReferenceType referenceType, String referenceId, String client) {
        LOGGER.debug("findByClient({}, {}, {})", referenceType, referenceId, client);
        return formRepository.findByClient(referenceType.name(), referenceId, client)
                .map(this::toEntity);
    }

    @Override
    public Maybe<Form> findByTemplate(ReferenceType referenceType, String referenceId, String template) {
        LOGGER.debug("findByTemplate({}, {}, {})", referenceType, referenceId, template);
        return formRepository.findByTemplate(referenceType.name(), referenceId, template)
                .map(this::toEntity);
    }

    @Override
    public Maybe<Form> findByClientAndTemplate(ReferenceType referenceType, String referenceId, String client, String template) {
        LOGGER.debug("findByClientAndTemplate({}, {}, {}, {})", referenceType, referenceId, client, template);
        return formRepository.findByClientAndTemplate(referenceType.name(), referenceId, client, template)
                .map(this::toEntity);
    }

    @Override
    public Maybe<Form> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("findById({}, {}, {})", referenceType, referenceId, id);
        return formRepository.findById(referenceType.name(), referenceId, id)
                .map(this::toEntity);
    }

    @Override
    public Maybe<Form> findById(String id) {
        LOGGER.debug("findById({})", id);
        return formRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<Form> create(Form item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create forms with id {}", item.getId());

        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity);
    }

    @Override
    public Single<Form> update(Form item) {
        LOGGER.debug("update forms with id {}", item.getId());
        return this.formRepository.save(toJdbcEntity(item))
                .map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return formRepository.deleteById(id);
    }
}
