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
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.FormRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
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
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class FormServiceImpl implements FormService {

    private final Logger LOGGER = LoggerFactory.getLogger(FormServiceImpl.class);

    @Lazy
    @Autowired
    private FormRepository formRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<Form> findById(String id) {
        LOGGER.debug("Find form by id {}", id);
        return formRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a form using its id {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a form using its id %s", id), ex));
                });
    }

    @Override
    public Single<List<Form>> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("Find form by {} {}", referenceType, referenceId);
        return formRepository.findAll(referenceType, referenceId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a form using its {} {}", referenceType, referenceId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a form using its %s %s", referenceType, referenceId), ex));
                });
    }

    @Override
    public Single<List<Form>> findByDomain(String domain) {
        return findAll(ReferenceType.DOMAIN, domain);
    }

    @Override
    public Single<List<Form>> findByClient(ReferenceType referenceType, String referenceId, String client) {
        LOGGER.debug("Find form by {} {} and client {}", referenceType, referenceId, client);
        return formRepository.findByClient(referenceType, referenceId, client)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a form using its {} {} and its client {}", referenceType, referenceId, client, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a form using its %s %s and client %s", referenceType, referenceId, client), ex));
                });
    }

    @Override
    public Single<List<Form>> findByDomainAndClient(String domain, String client) {
        LOGGER.debug("Find form by domain {} and client", domain, client);
        return findByClient(ReferenceType.DOMAIN, domain, client);
    }

    @Override
    public Maybe<Form> findByTemplate(ReferenceType referenceType, String referenceId, String template) {
        LOGGER.debug("Find form by {} {} and template {}", referenceType, referenceId, template);
        return formRepository.findByTemplate(referenceType, referenceId, template)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a form using its {} {} and template {}", referenceType, referenceId, template, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a form using its domain %s %s and template %s", referenceType, referenceId, template), ex));
                });
    }

    @Override
    public Maybe<Form> findByDomainAndTemplate(String domain, String template) {
        LOGGER.debug("Find form by domain {} and template {}", domain, template);
        return findByTemplate(ReferenceType.DOMAIN, domain, template);
    }

    @Override
    public Maybe<Form> findByClientAndTemplate(ReferenceType referenceType, String referenceId, String client, String template) {
        LOGGER.debug("Find form by {} {}, client {} and template {}", referenceType, referenceId, client, template);
        return formRepository.findByClientAndTemplate(referenceType, referenceId, client, template)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a form using its {} {} its client {} and template {}", referenceType, referenceId, client, template, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a form using its %s %s its client %s and template %s", referenceType, referenceId, client, template), ex));
                });
    }

    @Override
    public Maybe<Form> findByDomainAndClientAndTemplate(String domain, String client, String template) {
        LOGGER.debug("Find form by domain {}, client {} and template {}", domain, client, template);
        return findByClientAndTemplate(ReferenceType.DOMAIN, domain, client, template);
    }

    @Override
    public Single<List<Form>> copyFromClient(String domain, String clientSource, String clientTarget) {
        return findByDomainAndClient(domain, clientSource)
                .flatMapPublisher(Flowable::fromIterable)
                .flatMapSingle(source -> {
                    NewForm form = new NewForm();
                    form.setEnabled(source.isEnabled());
                    form.setTemplate(Template.parse(source.getTemplate()));
                    form.setContent(source.getContent());
                    form.setAssets(source.getAssets());
                    return this.create(domain, clientTarget, form);
                })
                .toList();
    }

    @Override
    public Single<Form> create(ReferenceType referenceType, String referenceId, NewForm newForm, User principal) {
        LOGGER.debug("Create a new form {} for {} {}", newForm, referenceType, referenceId);
        return create0(referenceType, referenceId, null, newForm, principal);
    }

    @Override
    public Single<Form> create(String domain, NewForm newForm, User principal) {
        LOGGER.debug("Create a new form {} for domain {}", newForm, domain);
        return create0(ReferenceType.DOMAIN, domain, null, newForm, principal);
    }

    @Override
    public Single<Form> create(String domain, String client, NewForm newForm, User principal) {
        LOGGER.debug("Create a new form {} for domain {} and client {}", newForm, domain, client);
        return create0(ReferenceType.DOMAIN, domain, client, newForm, principal);
    }

    @Override
    public Single<Form> update(ReferenceType referenceType, String referenceId, String id, UpdateForm updateForm, User principal) {
        LOGGER.debug("Update a form {} for {}} {}", id, referenceType, referenceId);

        return formRepository.findById(referenceType, referenceId, id)
                .switchIfEmpty(Maybe.error(new FormNotFoundException(id)))
                .flatMapSingle(oldForm -> {
                    Form formToUpdate = new Form(oldForm);
                    formToUpdate.setEnabled(updateForm.isEnabled());
                    formToUpdate.setContent(updateForm.getContent());
                    formToUpdate.setAssets(updateForm.getAssets());
                    formToUpdate.setUpdatedAt(new Date());

                    return formRepository.update(formToUpdate)
                            .flatMap(page -> {
                                // create event for sync process
                                Event event = new Event(Type.FORM, new Payload(page.getId(), page.getReferenceType(), page.getReferenceId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(page));
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
    public Single<Form> update(String domain, String id, UpdateForm updateForm, User principal) {
        LOGGER.debug("Update a form {} for domain {}", id, domain);
        return update(ReferenceType.DOMAIN, domain, id, updateForm, principal);
    }

    @Override
    public Single<Form> update(String domain, String client, String id, UpdateForm updateForm, User principal) {
        LOGGER.debug("Update a form {} for domain {} and client {}", id, domain, client);
        return update(ReferenceType.DOMAIN, domain, id, updateForm, principal);
    }

    private Single<Form> create0(ReferenceType referenceType, String referenceId, String client, NewForm newForm, User principal) {
        String formId = RandomString.generate();

        // check if form is unique
        return checkFormUniqueness(referenceType, referenceId, client, newForm.getTemplate().template())
                .flatMap(irrelevant -> {
                    Form form = new Form();
                    form.setId(formId);
                    form.setReferenceType(referenceType);
                    form.setReferenceId(referenceId);
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
                    // create event for sync process
                    Event event = new Event(Type.FORM, new Payload(page.getId(), page.getReferenceType(), page.getReferenceId(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(page));
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

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String formId, User principal) {
        LOGGER.debug("Delete form {}", formId);
        return formRepository.findById(referenceType, referenceId, formId)
                .switchIfEmpty(Maybe.error(new FormNotFoundException(formId)))
                .flatMapCompletable(page -> {
                    // create event for sync process
                    Event event = new Event(Type.FORM, new Payload(page.getId(), page.getReferenceType(), page.getReferenceId(), Action.DELETE));

                    return formRepository.delete(formId)
                            .andThen(eventService.create(event)).toCompletable()
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

    @Override
    public Completable delete(String domain, String formId, User principal) {

        return delete(ReferenceType.DOMAIN, domain, formId, principal);
    }

    private Single<Boolean> checkFormUniqueness(ReferenceType referenceType, String referenceId, String client, String formTemplate) {
        Maybe<Form> maybeSource = client == null ?
                findByTemplate(referenceType, referenceId, formTemplate) :
                findByClientAndTemplate(referenceType, referenceId, client, formTemplate);

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
