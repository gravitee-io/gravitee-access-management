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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.common.event.Action;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.common.event.Type;
import io.gravitee.am.repository.management.api.FormRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.FormAlreadyExistsException;
import io.gravitee.am.service.exception.FormNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewForm;
import io.gravitee.am.service.model.UpdateForm;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.FormTemplateAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

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

    @Autowired
    private AuditService auditService;

    @Override
    public Single<List<Form>> findByDomain(String domain) {
        LOGGER.debug("Find form by domain {}", domain);
        return formRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a form using its domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a role using its domain %s", domain), ex));
                });
    }

    @Override
    public Single<List<Form>> findByDomainAndClient(String domain, String client) {
        LOGGER.debug("Find form by domain {} and client", domain, client);
        return formRepository.findByDomainAndClient(domain, client)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a form using its domain {} and its client {}", domain, client, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a role using its domain %s and client %s", domain, client), ex));
                });
    }

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
    public Maybe<Form> findByDomainAndClientAndTemplate(String domain, String client, String template) {
        LOGGER.debug("Find form by domain {}, client {} and template {}", domain, client, template);
        return formRepository.findByDomainAndClientAndTemplate(domain, client, template)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a form using its domain {} its client {} and template {}", domain, client, template, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a form using its domain %s its client %s and template %s", domain, client, template), ex));
                });
    }

    @Override
    public Single<Form> create(String domain, NewForm newForm, User principal) {
        LOGGER.debug("Create a new form {} for domain {}", newForm, domain);
        return create0(domain, null, newForm, principal);
    }

    @Override
    public Single<Form> create(String domain, String client, NewForm newForm, User principal) {
        LOGGER.debug("Create a new form {} for domain {} and client {}", newForm, domain, client);
        return create0(domain, client, newForm, principal);
    }

    @Override
    public Single<Form> update(String domain, String id, UpdateForm updateForm, User principal) {
        LOGGER.debug("Update a form {} for domain {}", id, domain);
        return update0(domain, id, updateForm, principal);
    }

    @Override
    public Single<Form> update(String domain, String client, String id, UpdateForm updateForm, User principal) {
        LOGGER.debug("Update a form {} for domain {} and client {}", id, domain, client);
        return update0(domain, id, updateForm, principal);
    }

    private Single<Form> create0(String domain, String client, NewForm newForm, User principal) {
        String formId = RandomString.generate();

        // check if form is unique
        return checkFormUniqueness(domain, client, newForm.getTemplate().template())
                .flatMap(irrelevant -> {
                    Form form = new Form();
                    form.setId(formId);
                    form.setDomain(domain);
                    form.setClient(client);
                    form.setEnabled(newForm.isEnabled());
                    form.setTemplate(newForm.getTemplate().template());
                    form.setContent(newForm.getContent());
                    form.setAssets(newForm.getAssets());
                    form.setCreatedAt(new Date());
                    form.setUpdatedAt(form.getCreatedAt());
                    return formRepository.create(form);
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
                })
                .doOnSuccess(form -> auditService.report(AuditBuilder.builder(FormTemplateAuditBuilder.class).principal(principal).type(EventType.FORM_TEMPLATE_CREATED).form(form)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(FormTemplateAuditBuilder.class).principal(principal).type(EventType.FORM_TEMPLATE_CREATED).throwable(throwable)));
    }

    private Single<Form> update0(String domain, String id, UpdateForm updateForm, User principal) {
        return formRepository.findById(id)
                .switchIfEmpty(Maybe.error(new FormNotFoundException(id)))
                .flatMapSingle(oldForm -> {
                    Form formToUpdate = new Form(oldForm);
                    formToUpdate.setEnabled(updateForm.isEnabled());
                    formToUpdate.setContent(updateForm.getContent());
                    formToUpdate.setAssets(updateForm.getAssets());
                    formToUpdate.setUpdatedAt(new Date());

                    return formRepository.update(formToUpdate)
                            .flatMap(page -> {
                                // Reload domain to take care about form update
                                Event event = new Event(Type.FORM, new Payload(page.getId(), page.getDomain(), Action.UPDATE));
                                return domainService.reload(domain, event).flatMap(domain1 -> Single.just(page));
                            })
                            .doOnSuccess(form -> auditService.report(AuditBuilder.builder(FormTemplateAuditBuilder.class).principal(principal).type(EventType.FORM_TEMPLATE_UPDATED).oldValue(oldForm).form(form)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(FormTemplateAuditBuilder.class).principal(principal).type(EventType.FORM_TEMPLATE_UPDATED).throwable(throwable)));
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
    public Completable delete(String formId, User principal) {
        LOGGER.debug("Delete form {}", formId);
        return formRepository.findById(formId)
                .switchIfEmpty(Maybe.error(new FormNotFoundException(formId)))
                .flatMapCompletable(page -> {
                    // Reload domain to take care about delete form
                    Event event = new Event(Type.FORM, new Payload(page.getId(), page.getDomain(), Action.DELETE));
                    return formRepository.delete(formId)
                            .andThen(domainService.reload(page.getDomain(), event))
                            .toCompletable()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(FormTemplateAuditBuilder.class).principal(principal).type(EventType.FORM_TEMPLATE_DELETED).form(page)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(FormTemplateAuditBuilder.class).principal(principal).type(EventType.FORM_TEMPLATE_DELETED).throwable(throwable)));
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



    private Single<Boolean> checkFormUniqueness(String domain, String client, String formTemplate) {
        Maybe<Form> maybeSource = client == null ?
                findByDomainAndTemplate(domain, formTemplate) :
                findByDomainAndClientAndTemplate(domain, client, formTemplate);

        return maybeSource
                .isEmpty()
                .map(isEmpty -> {
                    if (!isEmpty) {
                        throw new FormAlreadyExistsException(formTemplate);
                    }
                    return true;
                });
    }
}
