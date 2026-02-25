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

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.AuthorizationSchema;
import io.gravitee.am.model.AuthorizationSchemaVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.AuthorizationSchemaRepository;
import io.gravitee.am.repository.management.api.AuthorizationSchemaVersionRepository;
import io.gravitee.am.service.AuthorizationSchemaService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthorizationSchemaNotFoundException;
import io.gravitee.am.service.exception.AuthorizationSchemaVersionNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.UpdateAuthorizationSchema;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author GraviteeSource Team
 */
@Component
@Primary
public class AuthorizationSchemaServiceImpl implements AuthorizationSchemaService {

    private final Logger LOGGER = LoggerFactory.getLogger(AuthorizationSchemaServiceImpl.class);

    private final AuthorizationSchemaRepository authorizationSchemaRepository;
    private final AuthorizationSchemaVersionRepository authorizationSchemaVersionRepository;
    private final EventService eventService;

    public AuthorizationSchemaServiceImpl(@Lazy AuthorizationSchemaRepository authorizationSchemaRepository,
                                          @Lazy AuthorizationSchemaVersionRepository authorizationSchemaVersionRepository,
                                          EventService eventService) {
        this.authorizationSchemaRepository = authorizationSchemaRepository;
        this.authorizationSchemaVersionRepository = authorizationSchemaVersionRepository;
        this.eventService = eventService;
    }

    @Override
    public Maybe<AuthorizationSchema> findByDomain(String domainId) {
        LOGGER.debug("Find authorization schema by domain: {}", domainId);
        return authorizationSchemaRepository.findByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization schema by domain: {}", domainId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find authorization schema by domain: %s", domainId), ex));
                });
    }

    @Override
    public Maybe<AuthorizationSchema> findById(String id) {
        LOGGER.debug("Find authorization schema by ID: {}", id);
        return authorizationSchemaRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization schema using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find authorization schema using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<AuthorizationSchema> createOrUpdate(Domain domain, UpdateAuthorizationSchema request, User principal) {
        LOGGER.debug("Create or update authorization schema for domain {}", domain.getId());

        return authorizationSchemaRepository.findByDomain(domain.getId())
                .flatMapSingle(existingSchema -> {
                    // Update existing schema
                    existingSchema.setEngineType(request.getEngineType());
                    existingSchema.setContent(request.getContent());
                    existingSchema.setVersion(existingSchema.getVersion() + 1);
                    existingSchema.setUpdatedAt(new Date());

                    return authorizationSchemaRepository.update(existingSchema)
                            .flatMap(updatedSchema -> createSchemaVersionRecord(updatedSchema, request.getComment(), principal)
                                    .flatMap(__ -> Single.just(updatedSchema)))
                            .flatMap(updatedSchema -> {
                                Event event = new Event(Type.AUTHORIZATION_SCHEMA, new Payload(updatedSchema.getId(), ReferenceType.DOMAIN, domain.getId(), Action.UPDATE));
                                return eventService.create(event, domain).flatMap(__ -> Single.just(updatedSchema));
                            });
                })
                .switchIfEmpty(Single.defer(() -> {
                    // Create new schema
                    AuthorizationSchema schema = new AuthorizationSchema();
                    schema.setId(RandomString.generate());
                    schema.setDomainId(domain.getId());
                    schema.setEngineType(request.getEngineType());
                    schema.setContent(request.getContent());
                    schema.setVersion(1);
                    schema.setCreatedAt(new Date());
                    schema.setUpdatedAt(schema.getCreatedAt());

                    return authorizationSchemaRepository.create(schema)
                            .flatMap(createdSchema -> {
                                AuthorizationSchemaVersion versionRecord = new AuthorizationSchemaVersion();
                                versionRecord.setId(RandomString.generate());
                                versionRecord.setSchemaId(createdSchema.getId());
                                versionRecord.setDomainId(domain.getId());
                                versionRecord.setVersion(1);
                                versionRecord.setContent(createdSchema.getContent());
                                versionRecord.setComment(request.getComment() != null ? request.getComment() : "Initial version");
                                versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                                versionRecord.setCreatedAt(createdSchema.getCreatedAt());

                                return authorizationSchemaVersionRepository.create(versionRecord)
                                        .flatMap(__ -> Single.just(createdSchema));
                            })
                            .flatMap(createdSchema -> {
                                Event event = new Event(Type.AUTHORIZATION_SCHEMA, new Payload(createdSchema.getId(), ReferenceType.DOMAIN, domain.getId(), Action.CREATE));
                                return eventService.create(event, domain).flatMap(__ -> Single.just(createdSchema));
                            });
                }))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create or update authorization schema", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create or update authorization schema", ex));
                });
    }

    @Override
    public Completable delete(Domain domain, User principal) {
        LOGGER.debug("Delete authorization schema for domain {}", domain.getId());

        return authorizationSchemaRepository.findByDomain(domain.getId())
                .switchIfEmpty(Maybe.error(new AuthorizationSchemaNotFoundException(domain.getId())))
                .flatMapCompletable(schema -> {
                    Event event = new Event(Type.AUTHORIZATION_SCHEMA, new Payload(schema.getId(), ReferenceType.DOMAIN, domain.getId(), Action.DELETE));

                    return authorizationSchemaVersionRepository.deleteBySchemaId(schema.getId())
                            .andThen(authorizationSchemaRepository.delete(schema.getId()))
                            .andThen(eventService.create(event, domain))
                            .ignoreElement();
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete authorization schema for domain: {}", domain.getId(), ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization schema for domain: %s", domain.getId()), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("Delete authorization schema by domain {}", domainId);
        return authorizationSchemaVersionRepository.deleteByDomain(domainId)
                .andThen(authorizationSchemaRepository.deleteByDomain(domainId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete authorization schema for domain: {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization schema for domain: %s", domainId), ex));
                });
    }

    @Override
    public Flowable<AuthorizationSchemaVersion> getVersionHistory(String domainId) {
        LOGGER.debug("Get version history for authorization schema in domain: {}", domainId);

        return authorizationSchemaRepository.findByDomain(domainId)
                .flatMapPublisher(schema -> authorizationSchemaVersionRepository.findBySchemaId(schema.getId()))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get version history for authorization schema in domain: {}", domainId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get version history for authorization schema in domain: %s", domainId), ex));
                });
    }

    @Override
    public Single<AuthorizationSchema> rollback(Domain domain, int targetVersion, User principal) {
        LOGGER.debug("Rollback authorization schema to version {} for domain {}", targetVersion, domain.getId());

        return authorizationSchemaRepository.findByDomain(domain.getId())
                .switchIfEmpty(Single.error(new AuthorizationSchemaNotFoundException(domain.getId())))
                .flatMap(existingSchema ->
                        authorizationSchemaVersionRepository.findBySchemaIdAndVersion(existingSchema.getId(), targetVersion)
                                .switchIfEmpty(Single.error(new AuthorizationSchemaVersionNotFoundException(existingSchema.getId(), targetVersion)))
                                .flatMap(targetVersionRecord -> {
                                    existingSchema.setContent(targetVersionRecord.getContent());
                                    existingSchema.setVersion(existingSchema.getVersion() + 1);
                                    existingSchema.setUpdatedAt(new Date());

                                    return authorizationSchemaRepository.update(existingSchema)
                                            .flatMap(updatedSchema -> {
                                                AuthorizationSchemaVersion versionRecord = new AuthorizationSchemaVersion();
                                                versionRecord.setId(RandomString.generate());
                                                versionRecord.setSchemaId(updatedSchema.getId());
                                                versionRecord.setDomainId(domain.getId());
                                                versionRecord.setVersion(updatedSchema.getVersion());
                                                versionRecord.setContent(targetVersionRecord.getContent());
                                                versionRecord.setComment("Rollback to version " + targetVersion);
                                                versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                                                versionRecord.setCreatedAt(updatedSchema.getUpdatedAt());

                                                return authorizationSchemaVersionRepository.create(versionRecord)
                                                        .flatMap(__ -> Single.just(updatedSchema));
                                            });
                                })
                )
                .flatMap(updatedSchema -> {
                    Event event = new Event(Type.AUTHORIZATION_SCHEMA, new Payload(updatedSchema.getId(), ReferenceType.DOMAIN, domain.getId(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(updatedSchema));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to rollback authorization schema for domain: {}", domain.getId(), ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to rollback authorization schema for domain: %s", domain.getId()), ex));
                });
    }

    private Single<AuthorizationSchemaVersion> createSchemaVersionRecord(AuthorizationSchema schema, String comment, User principal) {
        AuthorizationSchemaVersion versionRecord = new AuthorizationSchemaVersion();
        versionRecord.setId(RandomString.generate());
        versionRecord.setSchemaId(schema.getId());
        versionRecord.setDomainId(schema.getDomainId());
        versionRecord.setVersion(schema.getVersion());
        versionRecord.setContent(schema.getContent());
        versionRecord.setComment(comment);
        versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
        versionRecord.setCreatedAt(schema.getUpdatedAt());

        return authorizationSchemaVersionRepository.create(versionRecord);
    }
}
