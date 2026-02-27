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
import io.gravitee.am.model.AuthorizationBundle;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.AuthorizationBundleRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthorizationBundleService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthorizationBundleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthorizationBundle;
import io.gravitee.am.service.model.UpdateAuthorizationBundle;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.AuthorizationBundleAuditBuilder;
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
    private final EventService eventService;
    private final AuditService auditService;

    public AuthorizationBundleServiceImpl(@Lazy AuthorizationBundleRepository authorizationBundleRepository,
                                          EventService eventService,
                                          AuditService auditService) {
        this.authorizationBundleRepository = authorizationBundleRepository;
        this.eventService = eventService;
        this.auditService = auditService;
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
        bundle.setPolicySetId(request.getPolicySetId());
        bundle.setPolicySetVersion(request.getPolicySetVersion());
        bundle.setPolicySetPinToLatest(request.isPolicySetPinToLatest());
        bundle.setSchemaId(request.getSchemaId());
        bundle.setSchemaVersion(request.getSchemaVersion());
        bundle.setSchemaPinToLatest(request.isSchemaPinToLatest());
        bundle.setEntityStoreId(request.getEntityStoreId());
        bundle.setEntityStoreVersion(request.getEntityStoreVersion());
        bundle.setEntityStorePinToLatest(request.isEntityStorePinToLatest());
        bundle.setCreatedAt(new Date());
        bundle.setUpdatedAt(bundle.getCreatedAt());

        return authorizationBundleRepository.create(bundle)
                .flatMap(createdBundle -> {
                    Event event = new Event(Type.AUTHORIZATION_BUNDLE, new Payload(createdBundle.getId(), ReferenceType.DOMAIN, domain.getId(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(createdBundle));
                })
                .doOnSuccess(b -> auditService.report(AuditBuilder.builder(AuthorizationBundleAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_BUNDLE_CREATED).authorizationBundle(b)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationBundleAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_BUNDLE_CREATED).throwable(throwable)))
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
                    if (request.getPolicySetId() != null) {
                        bundleToUpdate.setPolicySetId(request.getPolicySetId());
                    }
                    if (request.getPolicySetVersion() != null) {
                        bundleToUpdate.setPolicySetVersion(request.getPolicySetVersion());
                    }
                    if (request.getSchemaId() != null) {
                        bundleToUpdate.setSchemaId(request.getSchemaId());
                    }
                    if (request.getSchemaVersion() != null) {
                        bundleToUpdate.setSchemaVersion(request.getSchemaVersion());
                    }
                    if (request.getEntityStoreId() != null) {
                        bundleToUpdate.setEntityStoreId(request.getEntityStoreId());
                    }
                    if (request.getEntityStoreVersion() != null) {
                        bundleToUpdate.setEntityStoreVersion(request.getEntityStoreVersion());
                    }
                    if (request.getPolicySetPinToLatest() != null) {
                        bundleToUpdate.setPolicySetPinToLatest(request.getPolicySetPinToLatest());
                    }
                    if (request.getSchemaPinToLatest() != null) {
                        bundleToUpdate.setSchemaPinToLatest(request.getSchemaPinToLatest());
                    }
                    if (request.getEntityStorePinToLatest() != null) {
                        bundleToUpdate.setEntityStorePinToLatest(request.getEntityStorePinToLatest());
                    }
                    bundleToUpdate.setUpdatedAt(new Date());

                    return authorizationBundleRepository.update(bundleToUpdate);
                })
                .flatMap(updatedBundle -> {
                    Event event = new Event(Type.AUTHORIZATION_BUNDLE, new Payload(updatedBundle.getId(), ReferenceType.DOMAIN, domain.getId(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(updatedBundle));
                })
                .doOnSuccess(b -> auditService.report(AuditBuilder.builder(AuthorizationBundleAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_BUNDLE_UPDATED).authorizationBundle(b)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationBundleAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_BUNDLE_UPDATED).throwable(throwable)))
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

                    return authorizationBundleRepository.delete(id)
                            .andThen(eventService.create(event, domain))
                            .ignoreElement()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(AuthorizationBundleAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_BUNDLE_DELETED).authorizationBundle(bundle)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(AuthorizationBundleAuditBuilder.class).principal(principal).type(EventType.AUTHORIZATION_BUNDLE_DELETED).throwable(throwable)));
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
        return authorizationBundleRepository.deleteByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete authorization bundles for domain: {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization bundles for domain: %s", domainId), ex));
                });
    }
}
