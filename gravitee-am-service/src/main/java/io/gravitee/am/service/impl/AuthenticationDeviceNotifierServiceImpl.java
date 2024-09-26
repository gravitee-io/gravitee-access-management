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
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.AuthenticationDeviceNotifierRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationDeviceNotifierService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthenticationDeviceNotifierNotFoundException;
import io.gravitee.am.service.exception.BotDetectionNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthenticationDeviceNotifier;
import io.gravitee.am.service.model.UpdateAuthenticationDeviceNotifier;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.AuthDeviceNotifierAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class AuthenticationDeviceNotifierServiceImpl implements AuthenticationDeviceNotifierService {

    @Lazy
    @Autowired
    private AuthenticationDeviceNotifierRepository adNotifierRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<AuthenticationDeviceNotifier> findById(String id) {
        log.debug("Find authentication device notifier by ID: {}", id);
        return adNotifierRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find an authentication device notifier using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an authentication device notifier using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<AuthenticationDeviceNotifier> findByDomain(String domain) {
        log.debug("Find authentication device notifiers by domain: {}", domain);
        return adNotifierRepository.findByReference(ReferenceType.DOMAIN, domain)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find authentication device notifiers by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find authentication device notifiers by domain", ex));
                });
    }

    @Override
    public Single<AuthenticationDeviceNotifier> create(String domain, NewAuthenticationDeviceNotifier newADNotifier, User principal) {
        log.debug("Create a new authentication device notifier {} for domain {}", newADNotifier, domain);

        AuthenticationDeviceNotifier notifier = new AuthenticationDeviceNotifier();
        notifier.setId(newADNotifier.getId() == null ? RandomString.generate() : newADNotifier.getId());
        notifier.setReferenceId(domain);
        notifier.setReferenceType(ReferenceType.DOMAIN);
        notifier.setName(newADNotifier.getName());
        notifier.setType(newADNotifier.getType());
        notifier.setConfiguration(newADNotifier.getConfiguration());
        notifier.setCreatedAt(new Date());
        notifier.setUpdatedAt(notifier.getCreatedAt());

        return adNotifierRepository.create(notifier)
                .flatMap(authDeviceNotifier -> {
                    // create event for sync process
                    Event event = new Event(Type.AUTH_DEVICE_NOTIFIER, new Payload(authDeviceNotifier.getId(), authDeviceNotifier.getReferenceType(), authDeviceNotifier.getReferenceId(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(authDeviceNotifier));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    log.error("An error occurs while trying to create a notifier", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a notifier", ex));
                })
                .doOnSuccess(authDeviceNotifier -> auditService.report(AuditBuilder.builder(AuthDeviceNotifierAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.AUTH_DEVICE_NOTIFIER_CREATED)
                        .authDeviceNotifier(authDeviceNotifier)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthDeviceNotifierAuditBuilder.class)
                        .principal(principal)
                        .reference(Reference.domain(domain))
                        .type(EventType.AUTH_DEVICE_NOTIFIER_CREATED)
                        .throwable(throwable)));
    }

    @Override
    public Single<AuthenticationDeviceNotifier> update(String domain, String id, UpdateAuthenticationDeviceNotifier updateNotifier, User principal) {
        log.debug("Update AuthenticationDevice Notifier {} for domain {}", id, domain);

        return adNotifierRepository.findById(id)
                .switchIfEmpty(Single.error(new BotDetectionNotFoundException(id)))
                .flatMap(oldNotifier -> {
                    AuthenticationDeviceNotifier notifierToUpdate = new AuthenticationDeviceNotifier(oldNotifier);
                    notifierToUpdate.setName(updateNotifier.getName());
                    notifierToUpdate.setConfiguration(updateNotifier.getConfiguration());
                    notifierToUpdate.setUpdatedAt(new Date());

                    return  adNotifierRepository.update(notifierToUpdate)
                            .flatMap(notifier -> {
                                // create event for sync process
                                Event event = new Event(Type.AUTH_DEVICE_NOTIFIER, new Payload(notifier.getId(), notifier.getReferenceType(), notifier.getReferenceId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(notifier));
                            })
                            .doOnSuccess(notifier -> auditService.report(AuditBuilder.builder(AuthDeviceNotifierAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.AUTH_DEVICE_NOTIFIER_UPDATED)
                                    .oldValue(oldNotifier)
                                    .authDeviceNotifier(notifier)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthDeviceNotifierAuditBuilder.class)
                                    .principal(principal)
                                    .reference(new Reference(oldNotifier.getReferenceType(), oldNotifier.getReferenceId()))
                                    .type(EventType.AUTH_DEVICE_NOTIFIER_UPDATED)
                                    .throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    log.error("An error occurs while trying to update authentication device notifier", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update authentication device notifier", ex));
                });
    }

    @Override
    public Completable delete(String domainId, String notifierId, User principal) {
        log.debug("Delete authentication device notifier {}", notifierId);

        return adNotifierRepository.findById(notifierId)
                .switchIfEmpty(Maybe.error(new AuthenticationDeviceNotifierNotFoundException(notifierId)))
                .flatMapCompletable(notifier -> {
                    // create event for sync process
                    Event event = new Event(Type.AUTH_DEVICE_NOTIFIER, new Payload(notifierId, ReferenceType.DOMAIN, domainId, Action.DELETE));
                    return Completable.fromSingle(adNotifierRepository.delete(notifierId)
                            .andThen(eventService.create(event)))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(AuthDeviceNotifierAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.AUTH_DEVICE_NOTIFIER_DELETED)
                                    .authDeviceNotifier(notifier)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthDeviceNotifierAuditBuilder.class)
                                    .principal(principal)
                                    .reference(new Reference(notifier.getReferenceType(), notifier.getReferenceId()))
                                    .type(EventType.AUTH_DEVICE_NOTIFIER_DELETED)
                                    .throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    log.error("An error occurs while trying to delete authentication device notifier: {}", notifierId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authentication device notifier: %s", notifierId), ex));
                });
    }

}
