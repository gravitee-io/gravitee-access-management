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
import io.gravitee.am.model.AuthorizationPolicy;
import io.gravitee.am.model.AuthorizationPolicyVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.AuthorizationPolicyRepository;
import io.gravitee.am.repository.management.api.AuthorizationPolicyVersionRepository;
import io.gravitee.am.service.AuthorizationPolicyService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.AuthorizationPolicyNotFoundException;
import io.gravitee.am.service.exception.AuthorizationPolicyVersionNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthorizationPolicy;
import io.gravitee.am.service.model.UpdateAuthorizationPolicy;
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
public class AuthorizationPolicyServiceImpl implements AuthorizationPolicyService {

    private final Logger LOGGER = LoggerFactory.getLogger(AuthorizationPolicyServiceImpl.class);

    private final AuthorizationPolicyRepository authorizationPolicyRepository;
    private final AuthorizationPolicyVersionRepository authorizationPolicyVersionRepository;
    private final EventService eventService;

    public AuthorizationPolicyServiceImpl(@Lazy AuthorizationPolicyRepository authorizationPolicyRepository,
                                          @Lazy AuthorizationPolicyVersionRepository authorizationPolicyVersionRepository,
                                          EventService eventService) {
        this.authorizationPolicyRepository = authorizationPolicyRepository;
        this.authorizationPolicyVersionRepository = authorizationPolicyVersionRepository;
        this.eventService = eventService;
    }

    @Override
    public Flowable<AuthorizationPolicy> findByDomain(String domainId) {
        LOGGER.debug("Find authorization policies by domain: {}", domainId);
        return authorizationPolicyRepository.findByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization policies by domain: {}", domainId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find authorization policies by domain: %s", domainId), ex));
                });
    }

    @Override
    public Maybe<AuthorizationPolicy> findById(String id) {
        LOGGER.debug("Find authorization policy by ID: {}", id);
        return authorizationPolicyRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an authorization policy using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an authorization policy using its ID: %s", id), ex));
                });
    }

    @Override
    public Maybe<AuthorizationPolicy> findByDomainAndId(String domainId, String id) {
        LOGGER.debug("Find authorization policy by domain {} and ID: {}", domainId, id);
        return authorizationPolicyRepository.findByDomainAndId(domainId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an authorization policy by domain {} and ID: {}", domainId, id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an authorization policy by domain %s and ID: %s", domainId, id), ex));
                });
    }

    @Override
    public Flowable<AuthorizationPolicy> findByDomainAndEngineType(String domainId, String engineType) {
        LOGGER.debug("Find authorization policies by domain {} and engine type: {}", domainId, engineType);
        return authorizationPolicyRepository.findByDomainAndEngineType(domainId, engineType)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find authorization policies by domain {} and engine type: {}", domainId, engineType, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find authorization policies by domain %s and engine type: %s", domainId, engineType), ex));
                });
    }

    @Override
    public Single<AuthorizationPolicy> create(Domain domain, NewAuthorizationPolicy request, User principal) {
        LOGGER.debug("Create a new authorization policy {} for domain {}", request, domain.getId());

        AuthorizationPolicy policy = new AuthorizationPolicy();
        policy.setId(RandomString.generate());
        policy.setDomainId(domain.getId());
        policy.setName(request.getName());
        policy.setDescription(request.getDescription());
        policy.setEngineType(request.getEngineType());
        policy.setContent(request.getContent());
        policy.setVersion(1);
        policy.setCreatedAt(new Date());
        policy.setUpdatedAt(policy.getCreatedAt());

        return authorizationPolicyRepository.create(policy)
                .flatMap(createdPolicy -> {
                    // Create initial version record
                    AuthorizationPolicyVersion versionRecord = new AuthorizationPolicyVersion();
                    versionRecord.setId(RandomString.generate());
                    versionRecord.setPolicyId(createdPolicy.getId());
                    versionRecord.setDomainId(domain.getId());
                    versionRecord.setVersion(1);
                    versionRecord.setContent(createdPolicy.getContent());
                    versionRecord.setComment("Initial version");
                    versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                    versionRecord.setCreatedAt(createdPolicy.getCreatedAt());

                    return authorizationPolicyVersionRepository.create(versionRecord)
                            .flatMap(__ -> Single.just(createdPolicy));
                })
                .flatMap(createdPolicy -> {
                    Event event = new Event(Type.AUTHORIZATION_POLICY, new Payload(createdPolicy.getId(), ReferenceType.DOMAIN, domain.getId(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(createdPolicy));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create an authorization policy", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an authorization policy", ex));
                });
    }

    @Override
    public Single<AuthorizationPolicy> update(Domain domain, String id, UpdateAuthorizationPolicy request, User principal) {
        LOGGER.debug("Update authorization policy {} for domain {}", id, domain.getId());

        return authorizationPolicyRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Single.error(new AuthorizationPolicyNotFoundException(id)))
                .flatMap(existingPolicy -> {
                    // Update policy fields
                    AuthorizationPolicy policyToUpdate = new AuthorizationPolicy(existingPolicy);
                    if (request.getName() != null) {
                        policyToUpdate.setName(request.getName());
                    }
                    if (request.getDescription() != null) {
                        policyToUpdate.setDescription(request.getDescription());
                    }
                    policyToUpdate.setContent(request.getContent());
                    policyToUpdate.setVersion(existingPolicy.getVersion() + 1);
                    policyToUpdate.setUpdatedAt(new Date());

                    return authorizationPolicyRepository.update(policyToUpdate)
                            .flatMap(updatedPolicy -> {
                                // Create version record with the new content
                                AuthorizationPolicyVersion versionRecord = new AuthorizationPolicyVersion();
                                versionRecord.setId(RandomString.generate());
                                versionRecord.setPolicyId(updatedPolicy.getId());
                                versionRecord.setDomainId(domain.getId());
                                versionRecord.setVersion(updatedPolicy.getVersion());
                                versionRecord.setContent(updatedPolicy.getContent());
                                versionRecord.setComment(request.getComment());
                                versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                                versionRecord.setCreatedAt(updatedPolicy.getUpdatedAt());

                                return authorizationPolicyVersionRepository.create(versionRecord)
                                        .flatMap(__ -> Single.just(updatedPolicy));
                            });
                })
                .flatMap(updatedPolicy -> {
                    Event event = new Event(Type.AUTHORIZATION_POLICY, new Payload(updatedPolicy.getId(), ReferenceType.DOMAIN, domain.getId(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(updatedPolicy));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update an authorization policy", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an authorization policy", ex));
                });
    }

    @Override
    public Completable delete(Domain domain, String id, User principal) {
        LOGGER.debug("Delete authorization policy {}", id);

        return authorizationPolicyRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new AuthorizationPolicyNotFoundException(id)))
                .flatMapCompletable(policy -> {
                    Event event = new Event(Type.AUTHORIZATION_POLICY, new Payload(id, ReferenceType.DOMAIN, domain.getId(), Action.DELETE));

                    return authorizationPolicyVersionRepository.deleteByPolicyId(id)
                            .andThen(authorizationPolicyRepository.delete(id))
                            .andThen(eventService.create(event, domain))
                            .ignoreElement();
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete authorization policy: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization policy: %s", id), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("Delete authorization policies by domain {}", domainId);
        return authorizationPolicyVersionRepository.deleteByDomain(domainId)
                .andThen(authorizationPolicyRepository.deleteByDomain(domainId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete authorization policies for domain: {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete authorization policies for domain: %s", domainId), ex));
                });
    }

    @Override
    public Flowable<AuthorizationPolicyVersion> getVersionHistory(String policyId) {
        LOGGER.debug("Get version history for authorization policy: {}", policyId);
        return authorizationPolicyVersionRepository.findByPolicyId(policyId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get version history for authorization policy: {}", policyId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get version history for authorization policy: %s", policyId), ex));
                });
    }

    @Override
    public Maybe<AuthorizationPolicyVersion> getVersion(String policyId, int version) {
        LOGGER.debug("Get version {} for authorization policy: {}", version, policyId);
        return authorizationPolicyVersionRepository.findByPolicyIdAndVersion(policyId, version)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get version {} for authorization policy: {}", version, policyId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get version %d for authorization policy: %s", version, policyId), ex));
                });
    }

    @Override
    public Single<AuthorizationPolicy> rollback(Domain domain, String policyId, int targetVersion, User principal) {
        LOGGER.debug("Rollback authorization policy {} to version {} for domain {}", policyId, targetVersion, domain.getId());

        return authorizationPolicyRepository.findByDomainAndId(domain.getId(), policyId)
                .switchIfEmpty(Single.error(new AuthorizationPolicyNotFoundException(policyId)))
                .flatMap(existingPolicy ->
                        authorizationPolicyVersionRepository.findByPolicyIdAndVersion(policyId, targetVersion)
                                .switchIfEmpty(Single.error(new AuthorizationPolicyVersionNotFoundException(policyId, targetVersion)))
                                .flatMap(targetVersionRecord -> {
                                    // Update policy with content from target version
                                    AuthorizationPolicy policyToUpdate = new AuthorizationPolicy(existingPolicy);
                                    policyToUpdate.setContent(targetVersionRecord.getContent());
                                    policyToUpdate.setVersion(existingPolicy.getVersion() + 1);
                                    policyToUpdate.setUpdatedAt(new Date());

                                    return authorizationPolicyRepository.update(policyToUpdate)
                                            .flatMap(updatedPolicy -> {
                                                // Create new version record for the rollback
                                                AuthorizationPolicyVersion versionRecord = new AuthorizationPolicyVersion();
                                                versionRecord.setId(RandomString.generate());
                                                versionRecord.setPolicyId(updatedPolicy.getId());
                                                versionRecord.setDomainId(domain.getId());
                                                versionRecord.setVersion(updatedPolicy.getVersion());
                                                versionRecord.setContent(targetVersionRecord.getContent());
                                                versionRecord.setComment("Rollback to version " + targetVersion);
                                                versionRecord.setCreatedBy(principal != null ? principal.getId() : null);
                                                versionRecord.setCreatedAt(updatedPolicy.getUpdatedAt());

                                                return authorizationPolicyVersionRepository.create(versionRecord)
                                                        .flatMap(__ -> Single.just(updatedPolicy));
                                            });
                                })
                )
                .flatMap(updatedPolicy -> {
                    Event event = new Event(Type.AUTHORIZATION_POLICY, new Payload(updatedPolicy.getId(), ReferenceType.DOMAIN, domain.getId(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(__ -> Single.just(updatedPolicy));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to rollback authorization policy: {}", policyId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to rollback authorization policy: %s", policyId), ex));
                });
    }
}
