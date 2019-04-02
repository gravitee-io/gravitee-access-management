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
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.common.event.Action;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.common.event.Type;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ExtensionGrantService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewExtensionGrant;
import io.gravitee.am.service.model.UpdateExtensionGrant;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ExtensionGrantAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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

    @Autowired
    private ExtensionGrantRepository extensionGrantRepository;

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

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
    public Single<List<ExtensionGrant>> findByDomain(String domain) {
        LOGGER.debug("Find extension grants by domain: {}", domain);
        return extensionGrantRepository.findByDomain(domain)
                .map(extensionGrants -> (List<ExtensionGrant>) new ArrayList<>(extensionGrants))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find extension grants by domain", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find extension grants by domain", ex));
                });
    }

    @Override
    public Single<ExtensionGrant> create(String domain, NewExtensionGrant newExtensionGrant, User principal) {
        LOGGER.debug("Create a new extension grant {} for domain {}", newExtensionGrant, domain);

        return extensionGrantRepository.findByDomainAndGrantType(domain, newExtensionGrant.getGrantType())
                .isEmpty()
                .flatMap(empty -> {
                    if (!empty) {
                        throw new ExtensionGrantAlreadyExistsException(newExtensionGrant.getGrantType());
                    } else {
                        String certificateId = RandomString.generate();
                        ExtensionGrant extensionGrant = new ExtensionGrant();
                        extensionGrant.setId(certificateId);
                        extensionGrant.setDomain(domain);
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
                                    // Reload domain to take care about extension grant create
                                    Event event = new Event(Type.EXTENSION_GRANT, new Payload(extensionGrant1.getId(), extensionGrant1.getDomain(), Action.CREATE));
                                    return domainService.reload(domain, event).flatMap(domain1 -> Single.just(extensionGrant1));
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
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).type(EventType.EXTENSION_GRANT_CREATED).throwable(throwable)));
    }

    @Override
    public Single<ExtensionGrant> update(String domain, String id, UpdateExtensionGrant updateExtensionGrant, User principal) {
        LOGGER.debug("Update a extension grant {} for domain {}", id, domain);

        return extensionGrantRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ExtensionGrantNotFoundException(id)))
                .flatMapSingle(tokenGranter -> extensionGrantRepository.findByDomainAndGrantType(domain, updateExtensionGrant.getGrantType())
                        .map(extensionGrant -> Optional.of(extensionGrant))
                        .defaultIfEmpty(Optional.empty())
                        .toSingle()
                        .flatMap(existingTokenGranter -> {
                           if (existingTokenGranter.isPresent() && !existingTokenGranter.get().getId().equals(id)) {
                               throw new ExtensionGrantAlreadyExistsException("Extension grant with the same grant type already exists");
                           }
                           return Single.just(tokenGranter);
                       }))
                .flatMap(oldExtensionGrant -> {
                    ExtensionGrant extensionGrantToUpdate = new ExtensionGrant(oldExtensionGrant);
                    extensionGrantToUpdate.setName(updateExtensionGrant.getName());
                    extensionGrantToUpdate.setGrantType(updateExtensionGrant.getGrantType());
                    extensionGrantToUpdate.setIdentityProvider(updateExtensionGrant.getIdentityProvider());
                    extensionGrantToUpdate.setCreateUser(updateExtensionGrant.isCreateUser());
                    extensionGrantToUpdate.setUserExists(updateExtensionGrant.isUserExists());
                    extensionGrantToUpdate.setConfiguration(updateExtensionGrant.getConfiguration());
                    extensionGrantToUpdate.setUpdatedAt(new Date());

                    return extensionGrantRepository.update(extensionGrantToUpdate)
                            .flatMap(extensionGrant -> {
                                // Reload domain to take care about extension grant update
                                Event event = new Event(Type.EXTENSION_GRANT, new Payload(extensionGrant.getId(), extensionGrant.getDomain(), Action.UPDATE));
                                return domainService.reload(domain, event).flatMap(domain1 -> Single.just(extensionGrant));
                            })
                            .doOnSuccess(extensionGrant -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).type(EventType.EXTENSION_GRANT_UPDATED).oldValue(oldExtensionGrant).extensionGrant(extensionGrant)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).type(EventType.EXTENSION_GRANT_UPDATED).throwable(throwable)));
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
                .flatMapSingle(extensionGrant -> clientService.findByDomainAndExtensionGrant(domain, extensionGrant.getGrantType())
                        .flatMap(clients -> {
                            if (clients.size() > 0) {
                                throw new ExtensionGrantWithClientsException();
                            }
                            return Single.just(extensionGrant);
                        }))
                .flatMapCompletable(extensionGrant -> {
                    // Reload domain to take care about extension grant create
                    Event event = new Event(Type.EXTENSION_GRANT, new Payload(extensionGrantId, domain, Action.DELETE));
                    return extensionGrantRepository.delete(extensionGrantId)
                            .andThen(domainService.reload(domain, event))
                            .toCompletable()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).type(EventType.EXTENSION_GRANT_DELETED).extensionGrant(extensionGrant)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ExtensionGrantAuditBuilder.class).principal(principal).type(EventType.EXTENSION_GRANT_DELETED).throwable(throwable)));
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
