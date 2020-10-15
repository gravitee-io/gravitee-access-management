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
package io.gravitee.am.identityprovider.api;

import io.gravitee.common.component.Lifecycle;
import io.gravitee.common.service.Service;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserProvider extends Service<UserProvider> {

    default Maybe<User> findByEmail(String email) {
        return Maybe.empty();
    }

    Maybe<User> findByUsername(String username);

    Single<User> create(User user);

    Single<User> update(String id, User updateUser);

    Completable delete(String id);

    default Lifecycle.State lifecycleState() {
        return Lifecycle.State.INITIALIZED;
    }

    default UserProvider start() throws Exception {
        return this;
    }

    default UserProvider stop() throws Exception {
        return this;
    }
}
