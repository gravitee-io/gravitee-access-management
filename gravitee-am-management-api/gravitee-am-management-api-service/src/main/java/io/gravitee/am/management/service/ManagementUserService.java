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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ManagementUserService {
    Single<User> updateUsername(Domain domain, String id, String username, io.gravitee.am.identityprovider.api.User principal);

    Maybe<User> findById(Domain domain, String id);

    Single<User> create(Domain domain, NewUser newUser, io.gravitee.am.identityprovider.api.User principal);

    Single<User> update(Domain domain, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal);

    Single<User> updateStatus(Domain domain, String id, boolean status, io.gravitee.am.identityprovider.api.User principal);

    Completable resetPassword(Domain domain, String userId, String password, io.gravitee.am.identityprovider.api.User principal);

    Completable sendRegistrationConfirmation(Domain domain, String userId, io.gravitee.am.identityprovider.api.User principal);

    Completable lock(Domain domain, String userId, io.gravitee.am.identityprovider.api.User principal);

    Completable unlock(Domain domain, String userId, io.gravitee.am.identityprovider.api.User principal);

    Single<User> assignRoles(Domain domain, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal);

    Single<User> revokeRoles(Domain domain, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal);

    Single<User> enrollFactors(Domain domain, String userId, List<EnrolledFactor> factors, io.gravitee.am.identityprovider.api.User principal);

    Single<User> unlinkIdentity(Domain domain, String userId, String identityId, io.gravitee.am.identityprovider.api.User principal);

    default Single<User> update(Domain domain, String id, UpdateUser updateUser) {
        return update(domain, id, updateUser, null);
    }

    default Completable unlock(Domain domain, String userId) {
        return unlock(domain, userId, null);
    }

    default Single<User> assignRoles(Domain domain, String userId, List<String> roles) {
        return assignRoles(domain, userId, roles, null);
    }

    default Single<User> revokeRoles(Domain domain, String userId, List<String> roles) {
        return revokeRoles(domain, userId, roles, null);
    }
    Single<User> delete(Domain domain, String userId, io.gravitee.am.identityprovider.api.User principal);

    Single<Page<User>> search(Domain domain, String query, int page, int size);

    Single<Page<User>> search(Domain domain, FilterCriteria filterCriteria, int page, int size);

    Single<Page<User>> findAll(Domain domain, int page, int size);
}
