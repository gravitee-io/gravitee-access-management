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
package io.gravitee.am.gateway.handler.root.service.user;

import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserService {

    Maybe<UserToken> verifyToken(String token);

    Single<User> register(User user, io.gravitee.am.identityprovider.api.User principal);

    Completable confirmRegistration(User user, io.gravitee.am.identityprovider.api.User principal);

    Completable resetPassword(User user, Client client, io.gravitee.am.identityprovider.api.User principal);

    Completable forgotPassword(String email, Client client, io.gravitee.am.identityprovider.api.User principal);

    default Single<User> register(User user) {
        return register(user, null);
    }

    default Completable resetPassword(User user, Client client) {
        return resetPassword(user, client, null);
    }

    default Completable forgotPassword(String email, Client client) {
        return forgotPassword(email, client, null);
    }

    default Completable confirmRegistration(User user) {
        return confirmRegistration(user, null);
    }

}
