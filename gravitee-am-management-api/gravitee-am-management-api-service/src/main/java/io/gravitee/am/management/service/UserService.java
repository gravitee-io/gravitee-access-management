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
package io.gravitee.am.management.service;

import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserService {

    Single<Page<User>> search(String domain, String query, int limit);

    Single<Page<User>> findByDomain(String domain, int page, int size);

    Maybe<User> findById(String id);

    Single<User> create(String domain, NewUser newUser);

    Single<User> update(String domain, String id, UpdateUser updateUser);

    Completable delete(String userId);

    Completable resetPassword(String domain, String userId, String password);

    Completable sendRegistrationConfirmation(String userId);
}
