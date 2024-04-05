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
import io.reactivex.rxjava3.core.Flowable;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.NewPasswordPolicy;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface PasswordPolicyService {


    Flowable<PasswordPolicy> findByDomain(String domain);

    /**
     * Create a new password policy linked to the reference entity (domain for example)
     *
     * @param referenceType
     * @param referenceId
     * @param policy
     * @param principal
     * @return
     */
    Single<PasswordPolicy> create(ReferenceType referenceType, String referenceId, NewPasswordPolicy policy, User principal);

}
