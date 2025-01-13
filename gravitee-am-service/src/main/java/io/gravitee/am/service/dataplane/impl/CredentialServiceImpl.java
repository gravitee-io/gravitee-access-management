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
package io.gravitee.am.service.dataplane.impl;

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.dataplane.CredentialService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.CredentialCurrentlyUsedException;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.factor.FactorSecurityType.WEBAUTHN_CREDENTIAL;
import static io.gravitee.am.model.ReferenceType.DOMAIN;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class CredentialServiceImpl implements CredentialService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialServiceImpl.class);

    @Lazy
    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Autowired
    private UserService userService;

    //FIXME do we have to keep RefTyp & RefId into the repository signatures ?

    @Override
    public Maybe<Credential> findById(Domain domain, String id) {
        LOGGER.debug("Find credential by ID: {}", id);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapMaybe(credentialRepository -> credentialRepository.findById(id))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a credential using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<Credential> findByUserId(Domain domain, String userId) {
        LOGGER.debug("Find credentials by Domain {} and user id: {}", domain.getId(), userId);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapPublisher(credentialRepository -> credentialRepository.findByUserId(DOMAIN, domain.getId(), userId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a credential using Domain {} and user id: {}", domain.getId(), userId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using Domain %s and user id: %s", domain.getId(), userId), ex));
                });
    }

    @Override
    public Flowable<Credential> findByUsername(Domain domain, String username) {
        LOGGER.debug("Find credentials by Domain {} and username: {}", domain.getId(), username);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapPublisher(credentialRepository -> credentialRepository.findByUsername(DOMAIN, domain.getId(), username))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a credential using Domain {} and username: {}", domain.getId(), username, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using Domain %s and username: %s", domain.getId(), username), ex));
                });
    }

    @Override
    public Flowable<Credential> findByUsername(Domain domain, String username, int limit) {
        LOGGER.debug("Find credentials by Domain {} and username: {}, returning {}", domain.getId(), username, limit);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapPublisher(credentialRepository -> credentialRepository.findByUsername(DOMAIN, domain.getId(), username, limit))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a credential using Domain {} and username: {} and limit: {}", domain.getId(), username, limit, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using Domain %s and username: %s", domain.getId(), username), ex));
                });
    }

    @Override
    public Flowable<Credential> findByCredentialId(Domain domain, String credentialId) {
        LOGGER.debug("Find credentials by Domain {} and credential ID: {}", domain.getId(), credentialId);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapPublisher(credentialRepository -> credentialRepository.findByCredentialId(DOMAIN, domain.getId(), credentialId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a credential using Domain {} and credential ID: {}", domain.getId(), credentialId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using Domain %s and credential ID: %s", domain.getId(), credentialId), ex));
                });
    }

    @Override
    public Single<Credential> create(Domain domain, Credential credential) {
        LOGGER.debug("Create a new credential {}", credential);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMap(credentialRepository -> credentialRepository.create(credential))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create a credential", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a credential", ex));
                });
    }

    @Override
    public Single<Credential> update(Domain domain, Credential credential) {
        LOGGER.debug("Update a credential {}", credential);
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
                    LOGGER.error("An error occurs while trying to update a credential", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a credential", ex));
                });
    }

    @Override
    public Single<Credential> update(Domain domain, String credentialId, Credential credential) {
        LOGGER.debug("Update a credential {}", credentialId);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMap(credentialRepository ->
                        credentialRepository.findByCredentialId(DOMAIN, domain.getId(), credentialId)
                                // filter on userId to restrict the credential to the current user.
                                // if credentialToUpdate has null userid, we are in the registration phase
                                // we want to assign this credential to the user profile, so we accept it.
                                .filter(credentialToUpdate -> credentialToUpdate.getUserId() == null || credentialToUpdate.getUserId().equals(credential.getUserId()))
                                .flatMapSingle(credentialToUpdate -> {
                                    // update only business values (i.e not set via the vert.x authenticator object)
                                    credentialToUpdate.setUserId(credential.getUserId());
                                    credentialToUpdate.setIpAddress(credential.getIpAddress());
                                    credentialToUpdate.setUserAgent(credential.getUserAgent());
                                    credentialToUpdate.setUpdatedAt(new Date());
                                    credentialToUpdate.setAccessedAt(credentialToUpdate.getUpdatedAt());
                                    credentialToUpdate.setLastCheckedAt(credential.getLastCheckedAt());
                                    return credentialRepository.update(credentialToUpdate);
                                })
                                .firstElement()
                                .switchIfEmpty(Single.error(() -> new CredentialNotFoundException(credentialId))));
    }

    @Override
    public Completable delete(Domain domain, String id) {
        return delete(domain, id, true);
    }

    @Override
    public Completable delete(Domain domain, String id, boolean enforceFactorDelete) {
        LOGGER.debug("Delete credential {}", id);
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
                            LOGGER.error("An error occurs while trying to delete credential: {}", id, ex);
                            return Completable.error(new TechnicalManagementException(
                                    String.format("An error occurs while trying to delete credential: %s", id), ex));
                        })
                );
    }

    @Override
    public Completable deleteByUserId(Domain domain, String userId) {
        LOGGER.debug("Delete credentials by {} {} and user id: {}", domain.getId(), userId);
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapCompletable(credentialRepository -> credentialRepository.deleteByUserId(DOMAIN, domain.getId(), userId))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error has occurred while trying to delete credentials using {} {} and user id: {}", domain.getId(), userId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error has occurred while trying to delete credentials using: %s %s and user id: %s", domain.getId(), userId), ex));
                });
    }

    @Override
    public Completable deleteByDomain(Domain domain) {
        LOGGER.debug("Delete credentials by reference {} {}", domain.getId());
        return dataPlaneRegistry.getCredentialRepository(domain)
                .flatMapCompletable(credentialRepository ->  credentialRepository.deleteByReference(DOMAIN, domain.getId()))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error has occurred while trying to delete credentials for {} {}", domain.getId(), ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error has occurred while trying to delete credentials for: %s %s", domain.getId()), ex));
                });
    }
}
