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

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.CredentialRepository;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class CredentialServiceImpl implements CredentialService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialServiceImpl.class);

    @Lazy
    @Autowired
    private CredentialRepository credentialRepository;

    @Override
    public Maybe<Credential> findById(String id) {
        LOGGER.debug("Find credential by ID: {}", id);
        return credentialRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a credential using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using its ID: %s", id), ex));
                });
    }

    @Override
    public Single<List<Credential>> findByUserId(ReferenceType referenceType, String referenceId, String userId) {
        LOGGER.debug("Find credentials by {} {} and user id: {}", referenceType, referenceId, userId);
        return credentialRepository.findByUserId(referenceType, referenceId, userId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a credential using {} {} and user id: {}", referenceType, referenceId, userId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using %s %s and user id: %s", referenceType, referenceId, userId), ex));
                });
    }

    @Override
    public Single<List<Credential>> findByUsername(ReferenceType referenceType, String referenceId, String username) {
        LOGGER.debug("Find credentials by {} {} and username: {}", referenceType, referenceId, username);
        return credentialRepository.findByUsername(referenceType, referenceId, username)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a credential using {} {} and username: {}", referenceType, referenceId, username, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using %s %s and username: %s", referenceType, referenceId, username), ex));
                });
    }

    @Override
    public Single<List<Credential>> findByCredentialId(ReferenceType referenceType, String referenceId, String credentialId) {
        LOGGER.debug("Find credentials by {} {} and credential ID: {}", referenceType, referenceId, credentialId);
        return credentialRepository.findByCredentialId(referenceType, referenceId, credentialId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a credential using {} {} and credential ID: {}", referenceType, referenceId, credentialId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a credential using %s %s and credential ID: %s", referenceType, referenceId, credentialId), ex));
                });
    }

    @Override
    public Single<Credential> create(Credential credential) {
        LOGGER.debug("Create a new credential {}", credential);
        return credentialRepository.create(credential)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create a credential", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a credential", ex));
                });
    }

    @Override
    public Single<Credential> update(Credential credential) {
        LOGGER.debug("Update a credential {}", credential);
        return credentialRepository.findById(credential.getId())
                .switchIfEmpty(Maybe.error(new CredentialNotFoundException(credential.getId())))
                .flatMapSingle(__ -> credentialRepository.update(credential))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a credential", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a credential", ex));
                });
    }

    @Override
    public Completable update(ReferenceType referenceType, String referenceId, String credentialId, String userId) {
        LOGGER.debug("Update a credential {}", credentialId);
        return credentialRepository.findByCredentialId(referenceType, referenceId, credentialId)
                .flatMapObservable(credentials -> Observable.fromIterable(credentials))
                .flatMapSingle(credential -> {
                    credential.setUserId(userId);
                    credential.setUpdatedAt(new Date());
                    return credentialRepository.update(credential);
                })
                .ignoreElements();
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("Delete credential {}", id);
        return credentialRepository.findById(id)
                .switchIfEmpty(Maybe.error(new CredentialNotFoundException(id)))
                .flatMapCompletable(email -> credentialRepository.delete(id))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete credential: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete credential: %s", id), ex));
                });
    }
}
