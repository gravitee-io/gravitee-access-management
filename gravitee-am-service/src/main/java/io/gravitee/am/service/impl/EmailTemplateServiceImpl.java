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
import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.EmailRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.EmailAlreadyExistsException;
import io.gravitee.am.service.exception.EmailNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewEmail;
import io.gravitee.am.service.model.UpdateEmail;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.EmailTemplateAuditBuilder;
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
public class EmailTemplateServiceImpl implements EmailTemplateService {

    private final Logger LOGGER = LoggerFactory.getLogger(EmailTemplateServiceImpl.class);

    @Lazy
    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Single<List<Email>> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("Find all emails for {} {}", referenceType, referenceId);
        return emailRepository.findAll(referenceType, referenceId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all emails for {} {}", referenceType, referenceId, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find a all emails for %s %s", referenceType, referenceId), ex));
                });
    }

    @Override
    public Single<List<Email>> findAll() {
        LOGGER.debug("Find all emails");
        return emailRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all emails", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find a all emails", ex));
                });
    }

    @Override
    public Single<List<Email>> findByClient(ReferenceType referenceType, String referenceId, String client) {
        LOGGER.debug("Find email by {} {} and client {}", referenceType, referenceId, client);
        return emailRepository.findByClient(referenceType, referenceId, client)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a email using its {} {} and its client {}", referenceType, referenceId, client, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a email using its %s %s and its client %s", referenceType, referenceId, client), ex));
                });
    }

    @Override
    public Maybe<Email> findByTemplate(ReferenceType referenceType, String referenceId, String template) {
        LOGGER.debug("Find email by {} {} and template {}", referenceType, referenceId, template);
        return emailRepository.findByTemplate(referenceType, referenceId, template)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a email using its {} {} and template {}", referenceType, referenceId, template, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a email using its %s %s and template %s", referenceType, referenceId, template), ex));
                });
    }

    @Override
    public Maybe<Email> findByDomainAndTemplate(String domain, String template) {

        return findByTemplate(ReferenceType.DOMAIN, domain, template);
    }

    @Override
    public Maybe<Email> findByClientAndTemplate(ReferenceType referenceType, String referenceId, String client, String template) {
        LOGGER.debug("Find email by {} {}, client {} and template {}", referenceType, referenceId, client, template);
        return emailRepository.findByClientAndTemplate(referenceType, referenceId, client, template)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a email using its {} {} its client {} and template {}", referenceType, referenceId, client, template, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a email using its %s %s its client %s and template %s", referenceType, referenceId, client, template), ex));
                });
    }

    @Override
    public Maybe<Email> findByDomainAndClientAndTemplate(String domain, String client, String template) {
        return findByClientAndTemplate(ReferenceType.DOMAIN, domain, client, template);
    }

    @Override
    public Maybe<Email> findById(String id) {
        LOGGER.debug("Find email by id {}", id);
        return emailRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a email using its id {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a email using its id %s", id), ex));
                });
    }

    @Override
    public Single<List<Email>> copyFromClient(String domain, String clientSource, String clientTarget) {
        return findByClient(ReferenceType.DOMAIN, domain, clientSource)
                .flatMapPublisher(Flowable::fromIterable)
                .flatMapSingle(source -> {
                    NewEmail email = new NewEmail();
                    email.setEnabled(source.isEnabled());
                    email.setTemplate(Template.parse(source.getTemplate()));
                    email.setFrom(source.getFrom());
                    email.setFromName(source.getFromName());
                    email.setSubject(source.getSubject());
                    email.setContent(source.getContent());
                    email.setExpiresAfter(source.getExpiresAfter());
                    return this.create(domain, clientTarget, email);
                })
                .toList();
    }

    @Override
    public Single<Email> create(ReferenceType referenceType, String referenceId, NewEmail newEmail, User principal) {
        LOGGER.debug("Create a new email {} for {} {}", newEmail, referenceType, referenceId);
        return create0(referenceType, referenceId, null, newEmail, principal);
    }

    @Override
    public Single<Email> create(String domain, NewEmail newEmail, User principal) {
        return create(ReferenceType.DOMAIN, domain, newEmail, principal);
    }

    @Override
    public Single<Email> create(ReferenceType referenceType, String referenceId, String client, NewEmail newEmail, User principal) {
        LOGGER.debug("Create a new email {} for {} {} and client {}", newEmail, referenceType, referenceId, client);
        return create0(referenceType, referenceId, client, newEmail, principal);
    }

    @Override
    public Single<Email> create(String domain, String client, NewEmail newEmail, User principal) {
        return create(ReferenceType.DOMAIN, domain, client, newEmail, principal);
    }

    @Override
    public Single<Email> update(String domain, String id, UpdateEmail updateEmail, User principal) {
        LOGGER.debug("Update an email {} for domain {}", id, domain);
        return update0(ReferenceType.DOMAIN, domain, id, updateEmail, principal);
    }

    @Override
    public Single<Email> update(String domain, String client, String id, UpdateEmail updateEmail, User principal) {
        LOGGER.debug("Update an email {} for domain {} and client {}", id, domain, client);
        return update0(ReferenceType.DOMAIN, domain, id, updateEmail, principal);
    }

    @Override
    public Completable delete(String emailId, User principal) {
        LOGGER.debug("Delete email {}", emailId);
        return emailRepository.findById(emailId)
                .switchIfEmpty(Maybe.error(new EmailNotFoundException(emailId)))
                .flatMapCompletable(email -> {
                    // create event for sync process
                    Event event = new Event(Type.EMAIL, new Payload(email.getId(), email.getReferenceType(), email.getReferenceId(), Action.DELETE));
                    return emailRepository.delete(emailId)
                            .andThen(eventService.create(event))
                            .toCompletable()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(EmailTemplateAuditBuilder.class).principal(principal).type(EventType.EMAIL_TEMPLATE_DELETED).email(email)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(EmailTemplateAuditBuilder.class).principal(principal).type(EventType.EMAIL_TEMPLATE_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete email: {}", emailId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete email: %s", emailId), ex));
                });
    }


    private Single<Email> create0(ReferenceType referenceType, String referenceId, String client, NewEmail newEmail, User principal) {
        String emailId = RandomString.generate();

        // check if email is unique
        return checkEmailUniqueness(referenceType, referenceId, client, newEmail.getTemplate().template())
                .flatMap(irrelevant -> {
                    Email email = new Email();
                    email.setId(emailId);
                    email.setReferenceType(referenceType);
                    email.setReferenceId(referenceId);
                    email.setClient(client);
                    email.setEnabled(newEmail.isEnabled());
                    email.setTemplate(newEmail.getTemplate().template());
                    email.setFrom(newEmail.getFrom());
                    email.setFromName(newEmail.getFromName());
                    email.setSubject(newEmail.getSubject());
                    email.setContent(newEmail.getContent());
                    email.setExpiresAfter(newEmail.getExpiresAfter());
                    email.setCreatedAt(new Date());
                    email.setUpdatedAt(email.getCreatedAt());
                    return emailRepository.create(email);
                })
                .flatMap(email -> {
                    // create event for sync process
                    Event event = new Event(Type.EMAIL, new Payload(email.getId(), email.getReferenceType(), email.getReferenceId(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(email));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a email", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a email", ex));
                })
                .doOnSuccess(email -> auditService.report(AuditBuilder.builder(EmailTemplateAuditBuilder.class).principal(principal).type(EventType.EMAIL_TEMPLATE_CREATED).email(email)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(EmailTemplateAuditBuilder.class).principal(principal).type(EventType.EMAIL_TEMPLATE_CREATED).throwable(throwable)));
    }

    private Single<Email> update0(ReferenceType referenceType, String referenceId, String id, UpdateEmail updateEmail, User principal) {
        return emailRepository.findById(referenceType, referenceId, id)
                .switchIfEmpty(Maybe.error(new EmailNotFoundException(id)))
                .flatMapSingle(oldEmail -> {
                    Email emailToUpdate = new Email(oldEmail);
                    emailToUpdate.setEnabled(updateEmail.isEnabled());
                    emailToUpdate.setFrom(updateEmail.getFrom());
                    emailToUpdate.setFromName(updateEmail.getFromName());
                    emailToUpdate.setSubject(updateEmail.getSubject());
                    emailToUpdate.setContent(updateEmail.getContent());
                    emailToUpdate.setExpiresAfter(updateEmail.getExpiresAfter());
                    emailToUpdate.setUpdatedAt(new Date());

                    return emailRepository.update(emailToUpdate)
                            .flatMap(email -> {
                                // create event for sync process
                                Event event = new Event(Type.EMAIL, new Payload(email.getId(), email.getReferenceType(), email.getReferenceId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(email));
                            })
                            .doOnSuccess(email -> auditService.report(AuditBuilder.builder(EmailTemplateAuditBuilder.class).principal(principal).type(EventType.EMAIL_TEMPLATE_UPDATED).oldValue(oldEmail).email(email)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(EmailTemplateAuditBuilder.class).principal(principal).type(EventType.EMAIL_TEMPLATE_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a email", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a email", ex));
                });
    }

    private Single<Boolean> checkEmailUniqueness(ReferenceType referenceType, String referenceId, String client, String emailTemplate) {
        Maybe<Email> maybeSource = client == null ?
                findByTemplate(referenceType, referenceId, emailTemplate) :
                findByClientAndTemplate(referenceType, referenceId, client, emailTemplate);

        return maybeSource
                .isEmpty()
                .map(isEmpty -> {
                    if (!isEmpty) {
                        throw new EmailAlreadyExistsException(emailTemplate);
                    }
                    return true;
                });
    }
}
