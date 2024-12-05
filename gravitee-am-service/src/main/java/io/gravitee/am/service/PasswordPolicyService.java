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
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.PasswordSettingsAware;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.UpdatePasswordPolicy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rafal PODLES (rafal.podles at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface PasswordPolicyService {

    /**
     * Get all password policies by domain
     *
     * @param domain the domain
     * @return list of PasswordPolicy
     */

    Flowable<PasswordPolicy> findByDomain(String domain);

    /**
     * Create a new password policy linked to the reference entity (domain for example)
     *
     * @param policy the new password policy
     * @param principal the user performing the action
     * @return PasswordPolicy
     */
    Single<PasswordPolicy> create(PasswordPolicy policy, User principal);

    /**
     * Update a password policy linked to the reference entity (domain for example)
     *
     * @param referenceType the type of reference
     * @param referenceId the identifier of the reference
     * @param policyId the policy id to update
     * @param policy the new policy settings
     * @param principal the user
     * @return Password Policy
     */
    Single<PasswordPolicy> update(ReferenceType referenceType, String referenceId, String policyId, UpdatePasswordPolicy policy, User principal);

    /**
     * Retrieve a password policy by its reference type, reference ID, and policy ID.
     *
     * @param referenceType the type of reference (e.g., DOMAIN)
     * @param referenceId the identifier of the reference
     * @param policyId the ID of the password policy to retrieve
     * @return a Maybe emitting the retrieved PasswordPolicy, if found; otherwise, completes
     */
    Maybe<PasswordPolicy> findByReferenceAndId(ReferenceType referenceType, String referenceId, String policyId);

    /**
     * Delete the password policy and reset the policyId for each IDP linked to it
     *
     * @param referenceType the type of reference (e.g., DOMAIN)
     * @param referenceId the identifier of the reference
     * @param policyId the ID of the password policy to set as default
     * @param principal the principal user performing the operation
     * @return a Completable
     */
    Completable deleteAndUpdateIdp(ReferenceType referenceType, String referenceId, String policyId, User principal);

    /**
     * Delete password policies by the reference
     *
     * @param referenceType the type of reference (e.g., DOMAIN)
     * @param referenceId the identifier of the reference
     * @return a Completable
     */
    Completable deleteByReference(ReferenceType referenceType, String referenceId);

    /**
     * Retrieve the password policy associated with a user, based on the user's password settings awareness.
     *
     * @param user the user for whom to retrieve the password policy
     * @param passwordSettingsAware the object that is aware of the password settings
     * @param provider the identity provider
     * @return a Maybe emitting the retrieved PasswordPolicy, if found; otherwise, completes
     */
    Maybe<PasswordPolicy> retrievePasswordPolicy(io.gravitee.am.model.User user, PasswordSettingsAware passwordSettingsAware, IdentityProvider provider);

    /**
     * Set a password policy as the default policy for a reference entity (e.g., domain).
     *
     * @param referenceType the type of reference (e.g., DOMAIN)
     * @param referenceId the identifier of the reference
     * @param policyId the ID of the password policy to set as default
     * @param principal the principal user performing the operation
     * @return a Single emitting the updated PasswordPolicy that has been set as default
     */
    Single<PasswordPolicy> setDefaultPasswordPolicy(ReferenceType referenceType, String referenceId, String policyId, User principal);

    Single<PasswordPolicy> getDefaultPasswordPolicy(Reference domain);
}
