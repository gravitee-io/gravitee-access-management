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
import io.gravitee.am.model.AuthorizationBundle;
import io.gravitee.am.model.AuthorizationBundleVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.AuthorizationBundleRepository;
import io.gravitee.am.repository.management.api.AuthorizationBundleVersionRepository;
import io.gravitee.am.service.AuthorizationBundleService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthorizationBundleNotFoundException;
import io.gravitee.am.service.exception.AuthorizationBundleVersionNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthorizationBundle;
import io.gravitee.am.service.model.UpdateAuthorizationBundle;
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
public class AuthorizationBundleServiceImpl implements AuthorizationBundleService {

    private final Logger LOGGER = LoggerFactory.getLogger(AuthorizationBundleServiceImpl.class);

    private final AuthorizationBundleRepository authorizationBundleRepository;
    private final AuthorizationBundleVersionRepository authorizationBundleVersionRepository;
    private final EventService eventService;

    public AuthorizationBundleServiceImpl(@Lazy AuthorizationBundleRepository authorizationBundleRepository,
                                          @Lazy AuthorizationBundleVersionRepository authorizationBundleVersionRepository,
                                          EventService eventService) {
        this.authorizationBundleRepository = authorizationBundleRepository;
        this.authorizationBundleVersionRepository = authorizationBundleVersionRepository;
        this.eventService = eventService;
    }

    @Override
    public Flowable<AuthorizationBundle> findByDomain(String domainId) {
        LOGGER.debug("Find authorization bundles by domain: {}", domainId);
        return authorizationBundleRepository.findByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization bundles by domain: {}", domainId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find authorization bundles by domain: %s", domainId), ex));
                });
    }

    @Override
    public Maybe<AuthorizationBundle> findById(String id) {
        LOGGER.debug("Find authorization bundle by ID: {}", id);
        return authorizationBundleRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an authorization bundle using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an authorization bundle using its ID: %s", id), ex));
                });
    }

    @Override
    public Maybe<AuthorizationBundle> findByDomainAndId(String domainId, String id) {
        LOGGER.debug("Find authorization bundle by domain {} and ID: {}", domainId, id);
        return authorizationBundleRepository.findByDomainAndId(domainId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an authorization bundle by domain {} and ID: {}", domainId, id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an authorization bundle by domain %s and ID: %s", domainId, id), ex));
                });
    }

    @Override
    public Single<AuthorizationBundle> create(Domain domain, NewAuthorizationBundle request, User principal) {
        LOGGER.debug("Create a new authorization bundle {} for domain {}", request, domain.getId());

        AuthorizationBundle bundle = new AuthorizationBundle();
        bundle.setId(RandomString.generate());
        bundle.setDomainId(domain.getId());
        bundle.setName(request.getName());
        bundle.setDescription(request.getDescription());
        bundle.setEngineType(request.getEngineType());
        bundle.setSchema(request.getSchema());
        bundle.setPolicies(request.getPolicies());
        bundle.setEntities(request.getEntities());
        bundle.setVersion(1);
        bundle.setCreatedAt(new Date());
        bundle.setUpdatedAt(bundle.getCreatedAt());

        return authorizationBundleRepository.create(bundle)
                .flatMap(createdBundle -> {
                    AuthorizationBundleVersion versionRecord = new AuthorizationBundleVersion();
                    versionRecord.setId(RandomString.generate());
                    versionRecord.setBundleId(createdBundle.getId());
                    versionRecord.setDomainId(domain.getId());
                    versionRecord.setVersion(1);
                    versionRecord.setSchema(createdBundle.getSchema());
                    versionRecord.setPolicies(createdBundle.getPolicies());
                    versionRecord.setEntities(createdBundle.getEntities());
                    versionRecord.setComment("Initial version");
                    versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                    versionRecord.setCreatedAt(createdBundle.getCreatedAt());

                    return authorizationBundleVersionRepository.create(versionRecord)
                            .flatMap(__ -> Single.just(createdBundle));
                })
                .flatMap(createdBundle -> {
                    Event event = new Event(Type.AUTHORIZATION_BUNDLE, new Payload(createdBundle.getId(), ReferenceType.DOMAIN, domain.getId(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(createdBundle));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create an authorization bundle", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an authorization bundle", ex));
                });
    }

    @Override
    public Single<AuthorizationBundle> update(Domain domain, String id, UpdateAuthorizationBundle request, User principal) {
        LOGGER.debug("Update authorization bundle {} for domain {}", id, domain.getId());

        return authorizationBundleRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Single.error(new AuthorizationBundleNotFoundException(id)))
                .flatMap(existingBundle -> {
                    AuthorizationBundle bundleToUpdate = new AuthorizationBundle(existingBundle);
                    if (request.getName() != null) {
                        bundleToUpdate.setName(request.getName());
                    }
                    if (request.getDescription() != null) {
                        bundleToUpdate.setDescription(request.getDescription());
                    }
                    if (request.getSchema() != null) {
                        bundleToUpdate.setSchema(request.getSchema());
                    }
                    if (request.getPolicies() != null) {
                        bundleToUpdate.setPolicies(request.getPolicies());
                    }
                    if (request.getEntities() != null) {
                        bundleToUpdate.setEntities(request.getEntities());
                    }
                    bundleToUpdate.setVersion(existingBundle.getVersion() + 1);
                    bundleToUpdate.setUpdatedAt(new Date());

                    return authorizationBundleRepository.update(bundleToUpdate)
                            .flatMap(updatedBundle -> {
                                AuthorizationBundleVersion versionRecord = new AuthorizationBundleVersion();
                                versionRecord.setId(RandomString.generate());
                                versionRecord.setBundleId(updatedBundle.getId());
                                versionRecord.setDomainId(domain.getId());
                                versionRecord.setVersion(updatedBundle.getVersion());
                                versionRecord.setSchema(updatedBundle.getSchema());
                                versionRecord.setPolicies(updatedBundle.getPolicies());
                                versionRecord.setEntities(updatedBundle.getEntities());
                                versionRecord.setComment(request.getComment());
                                versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                                versionRecord.setCreatedAt(updatedBundle.getUpdatedAt());

                                return authorizationBundleVersionRepository.create(versionRecord)
                                        .flatMap(__ -> Single.just(updatedBundle));
                            });
                })
                .flatMap(updatedBundle -> {
                    Event event = new Event(Type.AUTHORIZATION_BUNDLE, new Payload(updatedBundle.getId(), ReferenceType.DOMAIN, domain.getId(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(updatedBundle));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update an authorization bundle", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an authorization bundle", ex));
                });
    }

    @Override
    public Completable delete(Domain domain, String id, User principal) {
        LOGGER.debug("Delete authorization bundle {}", id);

        return authorizationBundleRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new AuthorizationBundleNotFoundException(id)))
                .flatMapCompletable(bundle -> {
                    Event event = new Event(Type.AUTHORIZATION_BUNDLE, new Payload(id, ReferenceType.DOMAIN, domain.getId(), Action.DELETE));

                    return authorizationBundleVersionRepository.deleteByBundleId(id)
                            .andThen(authorizationBundleRepository.delete(id))
                            .andThen(eventService.create(event, domain))
                            .ignoreElement();
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete authorization bundle: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization bundle: %s", id), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("Delete authorization bundles by domain {}", domainId);
        return authorizationBundleVersionRepository.deleteByDomain(domainId)
                .andThen(authorizationBundleRepository.deleteByDomain(domainId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete authorization bundles for domain: {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization bundles for domain: %s", domainId), ex));
                });
    }

    @Override
    public Flowable<AuthorizationBundleVersion> getVersionHistory(String bundleId) {
        LOGGER.debug("Get version history for authorization bundle: {}", bundleId);
        return authorizationBundleVersionRepository.findByBundleId(bundleId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get version history for authorization bundle: {}", bundleId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get version history for authorization bundle: %s", bundleId), ex));
                });
    }

    @Override
    public Maybe<AuthorizationBundleVersion> getVersion(String bundleId, int version) {
        LOGGER.debug("Get version {} for authorization bundle: {}", version, bundleId);
        return authorizationBundleVersionRepository.findByBundleIdAndVersion(bundleId, version)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get version {} for authorization bundle: {}", version, bundleId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get version %d for authorization bundle: %s", version, bundleId), ex));
                });
    }

    @Override
    public Single<AuthorizationBundle> rollback(Domain domain, String bundleId, int targetVersion, User principal) {
        LOGGER.debug("Rollback authorization bundle {} to version {} for domain {}", bundleId, targetVersion, domain.getId());

        return authorizationBundleRepository.findByDomainAndId(domain.getId(), bundleId)
                .switchIfEmpty(Single.error(new AuthorizationBundleNotFoundException(bundleId)))
                .flatMap(existingBundle ->
                        authorizationBundleVersionRepository.findByBundleIdAndVersion(bundleId, targetVersion)
                                .switchIfEmpty(Single.error(new AuthorizationBundleVersionNotFoundException(bundleId, targetVersion)))
                                .flatMap(targetVersionRecord -> {
                                    AuthorizationBundle bundleToUpdate = new AuthorizationBundle(existingBundle);
                                    bundleToUpdate.setSchema(targetVersionRecord.getSchema());
                                    bundleToUpdate.setPolicies(targetVersionRecord.getPolicies());
                                    bundleToUpdate.setEntities(targetVersionRecord.getEntities());
                                    bundleToUpdate.setVersion(existingBundle.getVersion() + 1);
                                    bundleToUpdate.setUpdatedAt(new Date());

                                    return authorizationBundleRepository.update(bundleToUpdate)
                                            .flatMap(updatedBundle -> {
                                                AuthorizationBundleVersion versionRecord = new AuthorizationBundleVersion();
                                                versionRecord.setId(RandomString.generate());
                                                versionRecord.setBundleId(updatedBundle.getId());
                                                versionRecord.setDomainId(domain.getId());
                                                versionRecord.setVersion(updatedBundle.getVersion());
                                                versionRecord.setSchema(targetVersionRecord.getSchema());
                                                versionRecord.setPolicies(targetVersionRecord.getPolicies());
                                                versionRecord.setEntities(targetVersionRecord.getEntities());
                                                versionRecord.setComment("Rollback to version " + targetVersion);
                                                versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                                                versionRecord.setCreatedAt(updatedBundle.getUpdatedAt());

                                                return authorizationBundleVersionRepository.create(versionRecord)
                                                        .flatMap(__ -> Single.just(updatedBundle));
                                            });
                                })
                )
                .flatMap(updatedBundle -> {
                    Event event = new Event(Type.AUTHORIZATION_BUNDLE, new Payload(updatedBundle.getId(), ReferenceType.DOMAIN, domain.getId(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(updatedBundle));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to rollback authorization bundle: {}", bundleId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to rollback authorization bundle: %s", bundleId), ex));
                });
    }
}
