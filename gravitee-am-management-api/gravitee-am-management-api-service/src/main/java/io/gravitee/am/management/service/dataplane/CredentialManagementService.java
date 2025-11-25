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

package io.gravitee.am.management.service.dataplane;


import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.dataplane.CredentialCommonService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CredentialManagementService extends CredentialCommonService {

    Maybe<Credential> findById(Domain domain, String id);

    Flowable<Credential> findByUsername(Domain domain, String username, int limit);

    Flowable<Credential> findByCredentialId(Domain domain, String credentialId);

    Completable delete(Domain domain, String id);

    /**
     * Delete all WebAuthn credentials for a user.
     *
     * @param domain the domain
     * @param userId the user ID
     * @return completable
     */
    Completable deleteByUserId(Domain domain, String userId);

}
