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

package io.gravitee.am.management.service.dataplane.impl;


import io.gravitee.am.management.service.dataplane.CredentialManagementService;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.CredentialCurrentlyUsedException;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.factor.FactorSecurityType.WEBAUTHN_CREDENTIAL;
import static io.gravitee.am.model.ReferenceType.DOMAIN;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Slf4j
public class CredentialManagementServiceImpl implements CredentialManagementService {

    @Lazy
    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Autowired
    private UserService userService;

    //FIXME do we have to keep RefTyp & RefId into the repository signatures ?

    @Override
    public Maybe<Credential> findById(Domain domain, String id) {
        log.debug("Find credential by ID: {}", id);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapMaybe(credentialRepository -> credentialRepository.findById(id))
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a credential using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<Credential> findByUserId(Domain domain, String userId) {
        log.debug("Find credentials by Domain {} and user id: {}", domain.getId(), userId);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapPublisher(credentialRepository -> credentialRepository.findByUserId(DOMAIN, domain.getId(), userId))
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a credential using Domain {} and user id: {}", domain.getId(), userId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using Domain %s and user id: %s", domain.getId(), userId), ex));
                });
    }

    @Override
    public Flowable<Credential> findByUsername(Domain domain, String username) {
        log.debug("Find credentials by Domain {} and username: {}", domain.getId(), username);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapPublisher(credentialRepository -> credentialRepository.findByUsername(DOMAIN, domain.getId(), username))
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a credential using Domain {} and username: {}", domain.getId(), username, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using Domain %s and username: %s", domain.getId(), username), ex));
                });
    }

    @Override
    public Flowable<Credential> findByUsername(Domain domain, String username, int limit) {
        log.debug("Find credentials by Domain {} and username: {}, returning {}", domain.getId(), username, limit);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapPublisher(credentialRepository -> credentialRepository.findByUsername(DOMAIN, domain.getId(), username, limit))
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a credential using Domain {} and username: {} and limit: {}", domain.getId(), username, limit, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using Domain %s and username: %s", domain.getId(), username), ex));
                });
    }

    @Override
    public Flowable<Credential> findByCredentialId(Domain domain, String credentialId) {
        log.debug("Find credentials by Domain {} and credential ID: {}", domain.getId(), credentialId);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapPublisher(credentialRepository -> credentialRepository.findByCredentialId(DOMAIN, domain.getId(), credentialId))
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find a credential using Domain {} and credential ID: {}", domain.getId(), credentialId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using Domain %s and credential ID: %s", domain.getId(), credentialId), ex));
                });
    }

    @Override
    public Single<Credential> update(Domain domain, Credential credential) {
        log.debug("Update a credential {}", credential);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMap(credentialRepository ->
                        // FIXME: check if we really need to do a find here. Maybe useless if already call i higher method call
                        credentialRepository.findById(credential.getId())
                                .switchIfEmpty(Single.error(new CredentialNotFoundException(credential.getId())))
                                .flatMap(__ -> credentialRepository.update(credential)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to update a credential", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a credential", ex));
                });
    }

    @Override
    public Completable delete(Domain domain, String id) {
        return delete(domain, id, true);
    }

    @Override
    public Completable delete(Domain domain, String id, boolean enforceFactorDelete) {
        log.debug("Delete credential {}", id);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapCompletable(credentialRepository -> credentialRepository.findById(id)
                        .switchIfEmpty(Maybe.error(new CredentialNotFoundException(id)))
                        .flatMapCompletable(credential -> {
                            if (enforceFactorDelete) {
                                return userService.findById(UserId.internal(credential.getUserId())).flatMapCompletable(user -> {
                                    final List<EnrolledFactor> factors = user.getFactors();
                                    if (factors == null || factors.isEmpty()) {
                                        return credentialRepository.delete(id);
                                    }

                                    final Optional<EnrolledFactor> fido2Factor = factors
                                            .stream()
                                            .filter(enrolledFactor -> enrolledFactor.getSecurity() != null)
                                            .filter(enrolledFactor ->
                                                    WEBAUTHN_CREDENTIAL.equals(enrolledFactor.getSecurity().getType()) &&
                                                            enrolledFactor.getSecurity().getValue().equals(credential.getCredentialId())
                                            )
                                            .findFirst();
                                    if (fido2Factor.isPresent()) {
                                        CredentialCurrentlyUsedException exception = new CredentialCurrentlyUsedException(id, fido2Factor.get().getFactorId(), "Fido2 factor ");
                                        return Completable.error(exception);
                                    } else {
                                        return credentialRepository.delete(id);
                                    }
                                });
                            } else {
                                return credentialRepository.delete(id);
                            }
                        })
                        .onErrorResumeNext(ex -> {
                            if (ex instanceof AbstractManagementException) {
                                return Completable.error(ex);
                            }
                            log.error("An error occurs while trying to delete credential: {}", id, ex);
                            return Completable.error(new TechnicalManagementException(
                                    String.format("An error occurs while trying to delete credential: %s", id), ex));
                        })
                );
    }

    @Override
    public Completable deleteByDomain(Domain domain) {
        log.debug("Delete credentials by reference {}", domain.getId());
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapCompletable(credentialRepository ->  credentialRepository.deleteByReference(DOMAIN, domain.getId()))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    log.error("An error has occurred while trying to delete credentials for domain {}", domain.getId(), ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error has occurred while trying to delete credentials for: domain %s", domain.getId()), ex));
                });
    }
}
