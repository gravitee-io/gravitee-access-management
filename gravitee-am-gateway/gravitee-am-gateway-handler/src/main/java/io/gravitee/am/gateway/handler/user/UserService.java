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
package io.gravitee.am.gateway.handler.user;

import io.gravitee.am.gateway.handler.user.model.UserToken;
import io.gravitee.am.model.Client;
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

    Maybe<UserToken> verifyToken(String token);

    Single<User> register(User user);

    Completable confirmRegistration(User user);

    Completable resetPassword(User user);

    Completable forgotPassword(String email, Client client);

    Single<Set<ScopeApproval>> consents(String userId);

    Single<Set<ScopeApproval>> consents(String userId, String clientId);

    Maybe<ScopeApproval> consent(String consentId);

    Completable revokeConsent(String consentId);

    Completable revokeConsents(String userId);

    Completable revokeConsents(String userId, String clientId);
}
