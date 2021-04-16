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
package io.gravitee.am.gateway.handler.users.service;

import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserService {
    Maybe<User> findById(String id);

    Single<Set<ScopeApproval>> consents(String userId);

    Single<Set<ScopeApproval>> consents(String userId, String clientId);

    Maybe<ScopeApproval> consent(String consentId);

    Completable revokeConsent(String userId, String consentId, io.gravitee.am.identityprovider.api.User principal);

    Completable revokeConsents(String userId, io.gravitee.am.identityprovider.api.User principal);

    Completable revokeConsents(String userId, String clientId, io.gravitee.am.identityprovider.api.User principal);

    default Completable revokeConsent(String userId, String consentId) {
        return revokeConsent(userId, consentId, null);
    }

    default Completable revokeConsents(String userId) {
        return revokeConsents(userId, (io.gravitee.am.identityprovider.api.User) null);
    }

    default Completable revokeConsents(String userId, String clientId) {
        return revokeConsents(userId, clientId, null);
    }
}
