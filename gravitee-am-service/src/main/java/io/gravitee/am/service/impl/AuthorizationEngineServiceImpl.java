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
import io.gravitee.am.common.plugin.ValidationResult;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.authorizationengine.core.AuthorizationEnginePluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.repository.management.api.AuthorizationEngineRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthorizationEngineService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthorizationEngineAlreadyExistsException;
import io.gravitee.am.service.exception.AuthorizationEngineInvalidConfigurationException;
import io.gravitee.am.service.exception.AuthorizationEngineNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthorizationEngine;
import io.gravitee.am.service.model.UpdateAuthorizationEngine;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.AuthorizationEngineAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author GraviteeSource Team
 */
@Component
public class AuthorizationEngineServiceImpl implements AuthorizationEngineService {

    private final Logger LOGGER = LoggerFactory.getLogger(AuthorizationEngineServiceImpl.class);

    private final AuthorizationEngineRepository authorizationEngineRepository;
    private final EventService eventService;
    private final AuditService auditService;
    private final AuthorizationEnginePluginManager authorizationEnginePluginManager;

    public AuthorizationEngineServiceImpl(@Lazy AuthorizationEngineRepository authorizationEngineRepository,
                                         EventService eventService,
                                         AuditService auditService,
                                         AuthorizationEnginePluginManager authorizationEnginePluginManager) {
        this.authorizationEngineRepository = authorizationEngineRepository;
        this.eventService = eventService;
        this.auditService = auditService;
        this.authorizationEnginePluginManager = authorizationEnginePluginManager;
    }

    @Override
    public Maybe<AuthorizationEngine> findById(String id) {
        LOGGER.debug("Find authorization engine by ID: {}", id);
        return authorizationEngineRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an authorization engine using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an authorization engine using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<AuthorizationEngine> findAll() {
        LOGGER.debug("Find all authorization engines");
        return authorizationEngineRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization engines", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find authorization engines", ex));
                });
    }

    @Override
    public Flowable<AuthorizationEngine> findByDomain(String domain) {
        LOGGER.debug("Find authorization engines by domain: {}", domain);
        return authorizationEngineRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization engines by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find authorization engines by domain " + domain, ex));
                });
    }

    @Override
    public Single<AuthorizationEngine> create(String domainId, NewAuthorizationEngine newAuthorizationEngine, User principal) {
        LOGGER.debug("Create a new authorization engine {} for domain {}", newAuthorizationEngine, domainId);

        // Check if authorization engine of this type already exists in the domain
        return authorizationEngineRepository.findByDomainAndType(domainId, newAuthorizationEngine.getType())
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (!isEmpty) {
                        return Single.error(new AuthorizationEngineAlreadyExistsException(newAuthorizationEngine.getType()));
                    }

                    return validateConfiguration(newAuthorizationEngine.getType(), newAuthorizationEngine.getConfiguration())
                            .andThen(Single.defer(() -> {
                                AuthorizationEngine authorizationEngine = new AuthorizationEngine();
                                authorizationEngine.setId(newAuthorizationEngine.getId() == null ? RandomString.generate() : newAuthorizationEngine.getId());
                                authorizationEngine.setReferenceType(ReferenceType.DOMAIN);
                                authorizationEngine.setReferenceId(domainId);
                                authorizationEngine.setName(newAuthorizationEngine.getName());
                                authorizationEngine.setType(newAuthorizationEngine.getType());
                                authorizationEngine.setConfiguration(newAuthorizationEngine.getConfiguration());
                                authorizationEngine.setCreatedAt(new Date());
                                authorizationEngine.setUpdatedAt(authorizationEngine.getCreatedAt());

                                return authorizationEngineRepository.create(authorizationEngine);
                            }));
                })
                .flatMap(createdEngine -> {
                    // create event for sync process
                    Event event = new Event(Type.AUTHORIZATION_ENGINE, new Payload(createdEngine.getId(), createdEngine.getReferenceType(), createdEngine.getReferenceId(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(createdEngine));
                })
                .doOnSuccess(createdEngine -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.AUTHORIZATION_ENGINE_CREATED)
                        .authorizationEngine(createdEngine)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.AUTHORIZATION_ENGINE_CREATED)
                        .reference(new Reference(ReferenceType.DOMAIN, domainId))
                        .throwable(throwable)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create an authorization engine", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an authorization engine", ex));
                });
    }

    @Override
    public Single<AuthorizationEngine> update(String domainId, String id, UpdateAuthorizationEngine updateAuthorizationEngine, User principal) {
        LOGGER.debug("Update an authorization engine {} for domain {}", id, domainId);

        return authorizationEngineRepository.findByDomainAndId(domainId, id)
                .switchIfEmpty(Single.error(new AuthorizationEngineNotFoundException(id)))
                .flatMap(oldEngine -> validateConfiguration(oldEngine.getType(), updateAuthorizationEngine.getConfiguration())
                        .andThen(Single.defer(() -> {
                    AuthorizationEngine engineToUpdate = new AuthorizationEngine(oldEngine);
                    engineToUpdate.setName(updateAuthorizationEngine.getName());
                    engineToUpdate.setConfiguration(updateAuthorizationEngine.getConfiguration());
                    engineToUpdate.setUpdatedAt(new Date());

                    return authorizationEngineRepository.update(engineToUpdate)
                            .flatMap(updatedEngine -> {
                                // create event for sync process
                                Event event = new Event(Type.AUTHORIZATION_ENGINE, new Payload(updatedEngine.getId(), updatedEngine.getReferenceType(), updatedEngine.getReferenceId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(updatedEngine));
                            })
                            .doOnSuccess(updatedEngine -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.AUTHORIZATION_ENGINE_UPDATED)
                                    .oldValue(oldEngine)
                                    .authorizationEngine(updatedEngine)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.AUTHORIZATION_ENGINE_UPDATED)
                                    .reference(new Reference(oldEngine.getReferenceType(), oldEngine.getReferenceId()))
                                    .authorizationEngine(oldEngine)
                                    .throwable(throwable)));
                })))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update an authorization engine", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an authorization engine", ex));
                });
    }

    @Override
    public Completable delete(String domainId, String authorizationEngineId, User principal) {
        LOGGER.debug("Delete authorization engine {}", authorizationEngineId);

        return authorizationEngineRepository.findByDomainAndId(domainId, authorizationEngineId)
                .switchIfEmpty(Maybe.error(new AuthorizationEngineNotFoundException(authorizationEngineId)))
                .flatMapCompletable(authorizationEngine -> {
                    // create event for sync process
                    Event event = new Event(Type.AUTHORIZATION_ENGINE, new Payload(authorizationEngineId, ReferenceType.DOMAIN, domainId, Action.DELETE));

                    return authorizationEngineRepository.delete(authorizationEngineId)
                            .andThen(eventService.create(event))
                            .ignoreElement()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.AUTHORIZATION_ENGINE_DELETED)
                                    .authorizationEngine(authorizationEngine)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationEngineAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.AUTHORIZATION_ENGINE_DELETED)
                                    .reference(new Reference(ReferenceType.DOMAIN, domainId))
                                    .authorizationEngine(authorizationEngine)
                                    .throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete authorization engine: {}", authorizationEngineId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization engine: %s", authorizationEngineId), ex));
                });
    }

    private Completable validateConfiguration(String type, String configuration) {
        return Completable.fromAction(() -> {
                    ValidationResult validationResult = authorizationEnginePluginManager.validate(new ProviderConfiguration(type, configuration));
                    if (validationResult.failed()) {
                        LOGGER.warn("An error occurs while validating the authorization engine configuration. Failed message: {}", validationResult.failedMessage());
                        throw new AuthorizationEngineInvalidConfigurationException(validationResult.failedMessage());
                    }
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to validate authorization engine configuration", ex);
                    return Completable.error(new TechnicalManagementException("An error occurs while trying to validate authorization engine configuration", ex));
                });
    }
}
