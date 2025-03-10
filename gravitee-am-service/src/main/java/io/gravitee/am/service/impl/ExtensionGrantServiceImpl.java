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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.ExtensionGrantService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ExtensionGrantAlreadyExistsException;
import io.gravitee.am.service.exception.ExtensionGrantNotFoundException;
import io.gravitee.am.service.exception.ExtensionGrantWithApplicationsException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewExtensionGrant;
import io.gravitee.am.service.model.UpdateExtensionGrant;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ExtensionGrantAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ExtensionGrantServiceImpl implements ExtensionGrantService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(ExtensionGrantServiceImpl.class);

    @Lazy
    @Autowired
    private ExtensionGrantRepository extensionGrantRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<ExtensionGrant> findById(String id) {
        LOGGER.debug("Find extension grant by ID: {}", id);
        return extensionGrantRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an extension grant using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an extension grant using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<ExtensionGrant> findByDomain(String domain) {
        LOGGER.debug("Find extension grants by domain: {}", domain);
        return extensionGrantRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find extension grants by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find extension grants by domain", ex));
                });
    }

    @Override
    public Single<ExtensionGrant> create(Domain domain, NewExtensionGrant newExtensionGrant, User principal) {
        LOGGER.debug("Create a new extension grant {} for domain {}", newExtensionGrant, domain.getId());

        return extensionGrantRepository.findByDomainAndName(domain.getId(), newExtensionGrant.getName())
                .isEmpty()
                .flatMap(empty -> {
                    if (!empty) {
                        throw new ExtensionGrantAlreadyExistsException(newExtensionGrant.getName());
                    } else {
                        String extensionGrantId = RandomString.generate();
                        ExtensionGrant extensionGrant = new ExtensionGrant();
                        extensionGrant.setId(extensionGrantId);
                        extensionGrant.setDomain(domain.getId());
                        extensionGrant.setName(newExtensionGrant.getName());
                        extensionGrant.setGrantType(newExtensionGrant.getGrantType());
                        extensionGrant.setIdentityProvider(newExtensionGrant.getIdentityProvider());
                        extensionGrant.setCreateUser(newExtensionGrant.isCreateUser());
                        extensionGrant.setUserExists(newExtensionGrant.isUserExists());
                        extensionGrant.setType(newExtensionGrant.getType());
                        extensionGrant.setConfiguration(newExtensionGrant.getConfiguration());
                        extensionGrant.setCreatedAt(new Date());
                        extensionGrant.setUpdatedAt(extensionGrant.getCreatedAt());

                        return extensionGrantRepository.create(extensionGrant)
                                .flatMap(extensionGrant1 -> {
                                    // create event for sync process
                                    Event event = new Event(Type.EXTENSION_GRANT, new Payload(extensionGrant1.getId(), ReferenceType.DOMAIN, extensionGrant1.getDomain(), Action.CREATE));
                                    return eventService.create(event, domain).flatMap(__ -> Single.just(extensionGrant1));
                                });

                    }
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to create a extension grant", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a extension grant", ex));
                })
                .doOnSuccess(extensionGrant -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).type(EventType.EXTENSION_GRANT_CREATED).extensionGrant(extensionGrant)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).reference(Reference.domain(domain.getId())).type(EventType.EXTENSION_GRANT_CREATED).throwable(throwable)));
    }

    @Override
    public Single<ExtensionGrant> update(Domain domain, String id, UpdateExtensionGrant updateExtensionGrant, User principal) {
        LOGGER.debug("Update a extension grant {} for domain {}", id, domain.getId());

        return extensionGrantRepository.findById(id)
                .switchIfEmpty(Single.error(new ExtensionGrantNotFoundException(id)))
                .flatMap(tokenGranter -> extensionGrantRepository.findByDomainAndName(domain.getId(), updateExtensionGrant.getName())
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(existingTokenGranter -> {
                            if (existingTokenGranter.isPresent() && !existingTokenGranter.get().getId().equals(id)) {
                                throw new ExtensionGrantAlreadyExistsException("Extension grant with the same name already exists");
                            }
                            return Single.just(tokenGranter);
                        }))
                .flatMap(oldExtensionGrant -> {
                    ExtensionGrant extensionGrantToUpdate = new ExtensionGrant(oldExtensionGrant);
                    extensionGrantToUpdate.setName(updateExtensionGrant.getName());
                    extensionGrantToUpdate.setGrantType(updateExtensionGrant.getGrantType() != null ? updateExtensionGrant.getGrantType() : oldExtensionGrant.getGrantType());
                    extensionGrantToUpdate.setIdentityProvider(updateExtensionGrant.getIdentityProvider());
                    extensionGrantToUpdate.setCreateUser(updateExtensionGrant.isCreateUser());
                    extensionGrantToUpdate.setUserExists(updateExtensionGrant.isUserExists());
                    extensionGrantToUpdate.setConfiguration(updateExtensionGrant.getConfiguration());
                    extensionGrantToUpdate.setUpdatedAt(new Date());

                    return extensionGrantRepository.update(extensionGrantToUpdate)
                            .flatMap(extensionGrant -> {
                                // create event for sync process
                                Event event = new Event(Type.EXTENSION_GRANT, new Payload(extensionGrant.getId(), ReferenceType.DOMAIN, extensionGrant.getDomain(), Action.UPDATE));
                                return eventService.create(event, domain).flatMap(__ -> Single.just(extensionGrant));
                            })
                            .doOnSuccess(extensionGrant -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).type(EventType.EXTENSION_GRANT_UPDATED).oldValue(oldExtensionGrant).extensionGrant(extensionGrant)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).reference(Reference.domain(domain.getId())).type(EventType.EXTENSION_GRANT_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update a extension grant", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a extension grant", ex));
                });
    }

    @Override
    public Completable delete(String domain, String extensionGrantId, User principal) {
        LOGGER.debug("Delete extension grant {}", extensionGrantId);
        return extensionGrantRepository.findById(extensionGrantId)
                .switchIfEmpty(Maybe.error(new ExtensionGrantNotFoundException(extensionGrantId)))
                // check for clients using this extension grant
                .flatMapSingle(extensionGrant -> applicationService.findByDomainAndExtensionGrant(domain, extensionGrant.getGrantType() + "~" + extensionGrant.getId())
                        .flatMap(applications -> {
                            if (!applications.isEmpty()) {
                                throw new ExtensionGrantWithApplicationsException();
                            }
                            // backward compatibility, check for old clients configuration
                            return Single.zip(
                                    applicationService.findByDomainAndExtensionGrant(domain, extensionGrant.getGrantType()),
                                    findByDomain(domain).toList(),
                                    (clients1, extensionGrants) -> {
                                        if (clients1.isEmpty()) {
                                            return extensionGrant;
                                        }
                                        // if clients use this grant_type, check it is the oldest one
                                        Date minDate = Collections.min(extensionGrants.stream().map(ExtensionGrant::getCreatedAt).collect(Collectors.toList()));
                                        if (extensionGrant.getCreatedAt().equals(minDate)) {
                                            throw new ExtensionGrantWithApplicationsException();
                                        } else {
                                            return extensionGrant;
                                        }
                                    });
                        }))
                .flatMapCompletable(extensionGrant -> {
                    // create event for sync process
                    Event event = new Event(Type.EXTENSION_GRANT, new Payload(extensionGrantId, ReferenceType.DOMAIN, domain, Action.DELETE));
                    return Completable.fromSingle(extensionGrantRepository.delete(extensionGrantId)
                            .andThen(eventService.create(event)))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).type(EventType.EXTENSION_GRANT_DELETED).extensionGrant(extensionGrant)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).extensionGrant(extensionGrant).type(EventType.EXTENSION_GRANT_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to extension grant: {}", extensionGrantId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete extension grant: %s", extensionGrantId), ex));
                });
    }

}
