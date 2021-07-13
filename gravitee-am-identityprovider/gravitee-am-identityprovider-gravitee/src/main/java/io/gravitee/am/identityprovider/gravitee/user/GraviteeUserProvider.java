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
package io.gravitee.am.identityprovider.gravitee.user;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeUserProvider implements UserProvider {

    @Override
    public Maybe<User> findByUsername(String username) {
        return Maybe.empty();
    }

    @Override
    public Single<User> create(User user) {
        // create will be performed by the repository layer called by the OrganizationUserService
        return Single.just(user);
    }

    @Override
    public Single<User> update(String id, User updateUser) {
        // update will be performed by the repository layer called by the OrganizationUserService
        return Single.just(updateUser);
    }

    @Override
    public Completable delete(String id) {
        // delete will be performed by the repository layer called by the OrganizationUserService
        return Completable.complete();
    }
}
