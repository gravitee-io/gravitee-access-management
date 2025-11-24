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
package io.gravitee.am.dataplane.api.repository;

import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author GraviteeSource Team
 */
public interface CertificateCredentialRepository extends CrudRepository<CertificateCredential, String> {

    /**
     * Find all certificate credentials for a user
     */
    Flowable<CertificateCredential> findByUserId(ReferenceType referenceType, String referenceId, String userId);

    /**
     * Find certificate credential by thumbprint (primary lookup for duplicate detection)
     */
    Maybe<CertificateCredential> findByThumbprint(ReferenceType referenceType, String referenceId, String thumbprint);


    /**
     * Find certificate credentials by username
     */
    Flowable<CertificateCredential> findByUsername(ReferenceType referenceType, String referenceId, String username);



    /**
     * Delete all certificate credentials for a user
     */
    Completable deleteByUserId(ReferenceType referenceType, String referenceId, String userId);

    /**
     * Delete certificate credential by domain, user, and credential ID.
     * Validates that the credential exists and belongs to the specified user in the domain before deleting.
     * This is an atomic operation that combines validation and deletion.
     * 
     * @param referenceType the reference type (typically DOMAIN)
     * @param referenceId the domain ID
     * @param userId the user ID
     * @param credentialId the credential ID
     * @return Maybe containing the deleted credential if found and belongs to the user, empty otherwise
     */
    Maybe<CertificateCredential> deleteByDomainAndUserAndId(ReferenceType referenceType, String referenceId, String userId, String credentialId);

    /**
     * Delete all certificate credentials for a reference (domain).
     *
     * @param referenceType the reference type (typically DOMAIN)
     * @param referenceId the domain ID
     * @return completable
     */
    Completable deleteByReference(ReferenceType referenceType, String referenceId);
}

