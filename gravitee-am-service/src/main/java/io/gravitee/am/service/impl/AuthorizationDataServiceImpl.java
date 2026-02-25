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
import io.gravitee.am.model.AuthorizationData;
import io.gravitee.am.model.AuthorizationDataVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.AuthorizationDataRepository;
import io.gravitee.am.repository.management.api.AuthorizationDataVersionRepository;
import io.gravitee.am.service.AuthorizationDataService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthorizationDataNotFoundException;
import io.gravitee.am.service.exception.AuthorizationDataVersionNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.UpdateAuthorizationData;
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
public class AuthorizationDataServiceImpl implements AuthorizationDataService {

    private final Logger LOGGER = LoggerFactory.getLogger(AuthorizationDataServiceImpl.class);

    private final AuthorizationDataRepository authorizationDataRepository;
    private final AuthorizationDataVersionRepository authorizationDataVersionRepository;
    private final EventService eventService;

    public AuthorizationDataServiceImpl(@Lazy AuthorizationDataRepository authorizationDataRepository,
                                        @Lazy AuthorizationDataVersionRepository authorizationDataVersionRepository,
                                        EventService eventService) {
        this.authorizationDataRepository = authorizationDataRepository;
        this.authorizationDataVersionRepository = authorizationDataVersionRepository;
        this.eventService = eventService;
    }

    @Override
    public Maybe<AuthorizationData> findByDomain(String domainId) {
        LOGGER.debug("Find authorization data by domain: {}", domainId);
        return authorizationDataRepository.findByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization data by domain: {}", domainId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find authorization data by domain: %s", domainId), ex));
                });
    }

    @Override
    public Maybe<AuthorizationData> findById(String id) {
        LOGGER.debug("Find authorization data by ID: {}", id);
        return authorizationDataRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization data using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find authorization data using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<AuthorizationData> createOrUpdate(Domain domain, UpdateAuthorizationData request, User principal) {
        LOGGER.debug("Create or update authorization data for domain {}", domain.getId());

        return authorizationDataRepository.findByDomain(domain.getId())
                .flatMapSingle(existingData -> {
                    // Update existing data
                    existingData.setEngineType(request.getEngineType());
                    existingData.setContent(request.getContent());
                    existingData.setVersion(existingData.getVersion() + 1);
                    existingData.setUpdatedAt(new Date());

                    return authorizationDataRepository.update(existingData)
                            .flatMap(updatedData -> createDataVersionRecord(updatedData, request.getComment(), principal)
                                    .flatMap(__ -> Single.just(updatedData)))
                            .flatMap(updatedData -> {
                                Event event = new Event(Type.AUTHORIZATION_DATA, new Payload(updatedData.getId(), ReferenceType.DOMAIN, domain.getId(), Action.UPDATE));
                                return eventService.create(event, domain).flatMap(__ -> Single.just(updatedData));
                            });
                })
                .switchIfEmpty(Single.defer(() -> {
                    // Create new data
                    AuthorizationData data = new AuthorizationData();
                    data.setId(RandomString.generate());
                    data.setDomainId(domain.getId());
                    data.setEngineType(request.getEngineType());
                    data.setContent(request.getContent());
                    data.setVersion(1);
                    data.setCreatedAt(new Date());
                    data.setUpdatedAt(data.getCreatedAt());

                    return authorizationDataRepository.create(data)
                            .flatMap(createdData -> {
                                AuthorizationDataVersion versionRecord = new AuthorizationDataVersion();
                                versionRecord.setId(RandomString.generate());
                                versionRecord.setDataId(createdData.getId());
                                versionRecord.setDomainId(domain.getId());
                                versionRecord.setVersion(1);
                                versionRecord.setContent(createdData.getContent());
                                versionRecord.setComment(request.getComment() != null ? request.getComment() : "Initial version");
                                versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                                versionRecord.setCreatedAt(createdData.getCreatedAt());

                                return authorizationDataVersionRepository.create(versionRecord)
                                        .flatMap(__ -> Single.just(createdData));
                            })
                            .flatMap(createdData -> {
                                Event event = new Event(Type.AUTHORIZATION_DATA, new Payload(createdData.getId(), ReferenceType.DOMAIN, domain.getId(), Action.CREATE));
                                return eventService.create(event, domain).flatMap(__ -> Single.just(createdData));
                            });
                }))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create or update authorization data", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create or update authorization data", ex));
                });
    }

    @Override
    public Completable delete(Domain domain, User principal) {
        LOGGER.debug("Delete authorization data for domain {}", domain.getId());

        return authorizationDataRepository.findByDomain(domain.getId())
                .switchIfEmpty(Maybe.error(new AuthorizationDataNotFoundException(domain.getId())))
                .flatMapCompletable(data -> {
                    Event event = new Event(Type.AUTHORIZATION_DATA, new Payload(data.getId(), ReferenceType.DOMAIN, domain.getId(), Action.DELETE));

                    return authorizationDataVersionRepository.deleteByDataId(data.getId())
                            .andThen(authorizationDataRepository.delete(data.getId()))
                            .andThen(eventService.create(event, domain))
                            .ignoreElement();
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete authorization data for domain: {}", domain.getId(), ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization data for domain: %s", domain.getId()), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("Delete authorization data by domain {}", domainId);
        return authorizationDataVersionRepository.deleteByDomain(domainId)
                .andThen(authorizationDataRepository.deleteByDomain(domainId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete authorization data for domain: {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization data for domain: %s", domainId), ex));
                });
    }

    @Override
    public Flowable<AuthorizationDataVersion> getVersionHistory(String domainId) {
        LOGGER.debug("Get version history for authorization data in domain: {}", domainId);

        return authorizationDataRepository.findByDomain(domainId)
                .flatMapPublisher(data -> authorizationDataVersionRepository.findByDataId(data.getId()))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get version history for authorization data in domain: {}", domainId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get version history for authorization data in domain: %s", domainId), ex));
                });
    }

    @Override
    public Single<AuthorizationData> rollback(Domain domain, int targetVersion, User principal) {
        LOGGER.debug("Rollback authorization data to version {} for domain {}", targetVersion, domain.getId());

        return authorizationDataRepository.findByDomain(domain.getId())
                .switchIfEmpty(Single.error(new AuthorizationDataNotFoundException(domain.getId())))
                .flatMap(existingData ->
                        authorizationDataVersionRepository.findByDataIdAndVersion(existingData.getId(), targetVersion)
                                .switchIfEmpty(Single.error(new AuthorizationDataVersionNotFoundException(existingData.getId(), targetVersion)))
                                .flatMap(targetVersionRecord -> {
                                    existingData.setContent(targetVersionRecord.getContent());
                                    existingData.setVersion(existingData.getVersion() + 1);
                                    existingData.setUpdatedAt(new Date());

                                    return authorizationDataRepository.update(existingData)
                                            .flatMap(updatedData -> {
                                                AuthorizationDataVersion versionRecord = new AuthorizationDataVersion();
                                                versionRecord.setId(RandomString.generate());
                                                versionRecord.setDataId(updatedData.getId());
                                                versionRecord.setDomainId(domain.getId());
                                                versionRecord.setVersion(updatedData.getVersion());
                                                versionRecord.setContent(targetVersionRecord.getContent());
                                                versionRecord.setComment("Rollback to version " + targetVersion);
                                                versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                                                versionRecord.setCreatedAt(updatedData.getUpdatedAt());

                                                return authorizationDataVersionRepository.create(versionRecord)
                                                        .flatMap(__ -> Single.just(updatedData));
                                            });
                                })
                )
                .flatMap(updatedData -> {
                    Event event = new Event(Type.AUTHORIZATION_DATA, new Payload(updatedData.getId(), ReferenceType.DOMAIN, domain.getId(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(updatedData));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to rollback authorization data for domain: {}", domain.getId(), ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to rollback authorization data for domain: %s", domain.getId()), ex));
                });
    }

    private Single<AuthorizationDataVersion> createDataVersionRecord(AuthorizationData data, String comment, User principal) {
        AuthorizationDataVersion versionRecord = new AuthorizationDataVersion();
        versionRecord.setId(RandomString.generate());
        versionRecord.setDataId(data.getId());
        versionRecord.setDomainId(data.getDomainId());
        versionRecord.setVersion(data.getVersion());
        versionRecord.setContent(data.getContent());
        versionRecord.setComment(comment);
        versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
        versionRecord.setCreatedAt(data.getUpdatedAt());

        return authorizationDataVersionRepository.create(versionRecord);
    }
}
