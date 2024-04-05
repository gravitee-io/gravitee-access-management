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

import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.service.model.UpdatePasswordPolicy;
import io.reactivex.rxjava3.core.Flowable;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.PasswordSettingsAware;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.NewPasswordPolicy;
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
     * @param referenceType the type of reference
     * @param referenceId the identifier of the reference
     * @param policy the new password policy
     * @param principal the user performing the action
     * @return PasswordPolicy
     */
    Single<PasswordPolicy> create(ReferenceType referenceType, String referenceId, NewPasswordPolicy policy, User principal);

    /**
     * Update a password policy linked to the reference entity (domain for example)
     *
     * @param referenceType the type of reference
     * @param referenceId the identifier of the reference
     * @param policyId the policy id to update
     * @param policy the new policy settings
     * @param principal
     * @return
     */
    Single<PasswordPolicy> update(ReferenceType referenceType, String referenceId, String policyId, UpdatePasswordPolicy policy, User principal);

    Maybe<PasswordPolicy> findByReferenceAndId(ReferenceType referenceType, String referenceId, String policyId);

    Maybe<PasswordPolicy> retrievePasswordPolicy(io.gravitee.am.model.User user, PasswordSettingsAware passwordSettingsAware);
}
