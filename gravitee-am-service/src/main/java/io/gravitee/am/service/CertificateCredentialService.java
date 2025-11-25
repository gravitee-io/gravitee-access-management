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
package io.gravitee.am.service;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author GraviteeSource Team
 */
public interface CertificateCredentialService {

    /**
     * Enroll a certificate for a user.
     *
     * @param domain the domain
     * @param userId the user ID
     * @param certificatePem the PEM-encoded certificate
     * @param principal the authenticated user performing the action
     * @return the enrolled certificate credential
     */
    Single<CertificateCredential> enrollCertificate(Domain domain, String userId, String certificatePem, User principal);

    /**
     * Find all certificate credentials for a user.
     *
     * @param domain the domain
     * @param userId the user ID
     * @return flowable of certificate credentials
     */
    Flowable<CertificateCredential> findByUserId(Domain domain, String userId);

    /**
     * Find a certificate credential by ID.
     *
     * @param domain the domain
     * @param id the credential ID
     * @return maybe certificate credential
     */
    Maybe<CertificateCredential> findById(Domain domain, String id);

    /**
     * Find a certificate credential by username.
     *
     * @param domain the domain
     * @param username the username
     * @return maybe certificate credential
     */
    Flowable<CertificateCredential> findByDomainAndUsername(Domain domain, String username);

    /**
     * Delete a certificate credential.
     *
     * @param domain the domain
     * @param id the credential ID
     * @return completable
     */
    Completable delete(Domain domain, String id);

    /**
     * Delete a certificate credential by domain, user, and credential ID.
     * Validates that the credential exists and belongs to the specified user in the domain before deleting.
     * This is an atomic operation that combines validation and deletion.
     *
     * @param domain the domain
     * @param userId the user ID
     * @param credentialId the credential ID
     * @param principal the authenticated user performing the action
     * @return maybe containing the deleted credential if found and belongs to the user, empty otherwise
     */
    Maybe<CertificateCredential> deleteByDomainAndUserAndId(Domain domain, String userId, String credentialId, User principal);

    /**
     * Delete all certificate credentials for a user.
     *
     * @param domain the domain
     * @param userId the user ID
     * @return completable
     */
    Completable deleteByUserId(Domain domain, String userId);

    /**
     * Delete all certificate credentials for a domain.
     *
     * @param domain the domain
     * @return completable
     */
    Completable deleteByDomain(Domain domain);
}

