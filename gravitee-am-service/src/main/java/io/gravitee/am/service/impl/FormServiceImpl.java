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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.Form;
import io.gravitee.am.model.common.event.Action;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.common.event.Type;
import io.gravitee.am.repository.management.api.FormRepository;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.FormAlreadyExistsException;
import io.gravitee.am.service.exception.FormNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewForm;
import io.gravitee.am.service.model.UpdateForm;
import io.gravitee.common.utils.UUID;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class FormServiceImpl implements FormService {

    private final Logger LOGGER = LoggerFactory.getLogger(FormServiceImpl.class);

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private DomainService domainService;

    @Override
    public Maybe<Form> findByDomainAndTemplate(String domain, String template) {
        LOGGER.debug("Find form by domain {} and template {}", domain, template);
        return formRepository.findByDomainAndTemplate(domain, template)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a form using its domain {} and template {}", domain, template, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a role using its domain %s and template %s", domain, template), ex));
                });
    }

    @Override
    public Single<Form> create(String domain, NewForm newForm) {
        LOGGER.debug("Create a new page {} for domain {}", newForm, domain);

        String pageId = UUID.toString(UUID.random());

        // check if form is unique
        return checkFormUniqueness(domain, newForm.getTemplate().template())
                .flatMap(irrelevant -> {
                    Form page = new Form();
                    page.setId(pageId);
                    page.setDomain(domain);
                    page.setEnabled(newForm.isEnabled());
                    page.setTemplate(newForm.getTemplate().template());
                    page.setContent(newForm.getContent());
                    page.setAssets(newForm.getAssets());
                    page.setCreatedAt(new Date());
                    page.setUpdatedAt(page.getCreatedAt());
                    return formRepository.create(page);
                })
                .flatMap(page -> {
                    // Reload domain to take care about page creation
                    Event event = new Event(Type.FORM, new Payload(page.getId(), page.getDomain(), Action.CREATE));
                    return domainService.reload(domain, event).flatMap(domain1 -> Single.just(page));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a form", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a form", ex));
                });
    }

    @Override
    public Single<Form> update(String domain, String id, UpdateForm updateForm) {
        LOGGER.debug("Update a page {} for domain {}", id, domain);
        return formRepository.findById(id)
                .switchIfEmpty(Maybe.error(new FormNotFoundException(id)))
                .flatMapSingle(oldPage -> {
                    oldPage.setEnabled(updateForm.isEnabled());
                    oldPage.setContent(updateForm.getContent());
                    oldPage.setAssets(updateForm.getAssets());
                    oldPage.setUpdatedAt(new Date());

                    return formRepository.update(oldPage);
                })
                .flatMap(page -> {
                    // Reload domain to take care about form update
                    Event event = new Event(Type.FORM, new Payload(page.getId(), page.getDomain(), Action.UPDATE));
                    return domainService.reload(domain, event).flatMap(domain1 -> Single.just(page));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a form", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a form", ex));
                });
    }

    @Override
    public Completable delete(String formId) {
        LOGGER.debug("Delete form {}", formId);
        return formRepository.findById(formId)
                .switchIfEmpty(Maybe.error(new FormNotFoundException(formId)))
                .flatMapCompletable(page -> {
                    // Reload domain to take care about delete form
                    Event event = new Event(Type.FORM, new Payload(page.getId(), page.getDomain(), Action.DELETE));
                    return formRepository.delete(formId).andThen(domainService.reload(page.getDomain(), event)).toCompletable();
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete form: {}", formId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete form: %s", formId), ex));
                });
    }

    private Single<Boolean> checkFormUniqueness(String domain, String formTemplate) {
        return findByDomainAndTemplate(domain, formTemplate)
                .isEmpty()
                .map(isEmpty -> {
                    if (!isEmpty) {
                        throw new FormAlreadyExistsException(formTemplate);
                    }
                    return true;
                });
    }
}
