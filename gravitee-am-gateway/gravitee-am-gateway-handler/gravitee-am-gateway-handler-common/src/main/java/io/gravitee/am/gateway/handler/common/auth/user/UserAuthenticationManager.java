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
package io.gravitee.am.gateway.handler.common.auth.user;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserAuthenticationManager {

    Single<User> authenticate(Client client, Authentication authentication, boolean preAuthenticated);

    Maybe<User> loadPreAuthenticatedUser(String subject, Request request);

    Single<User> connect(io.gravitee.am.identityprovider.api.User user, Client client, Request request, boolean afterAuthentication);

    Single<User> connectWithPasswordless(Client client, String subject, Authentication authentication);

    default Single<User> authenticate(Client client, Authentication authentication) {
        return authenticate(client, authentication, false);
    }

    default Maybe<User> loadPreAuthenticatedUser(String subject) {
        return loadPreAuthenticatedUser(subject, null);
    }

    default Single<User> connect(io.gravitee.am.identityprovider.api.User user, Client client, Request request) {
        return connect(user, client, request, true);
    }

    default Single<User> connect(io.gravitee.am.identityprovider.api.User user, boolean afterAuthentication) {
        return connect(user, null, null, afterAuthentication);
    }

    default Single<User> connect(io.gravitee.am.identityprovider.api.User user) {
        return connect(user, null, null, true);
    }
}
