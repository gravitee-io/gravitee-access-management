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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserService {

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria, int page, int size);

    Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size);

    Single<Page<User>> findByDomain(String domain, int page, int size);

    Single<User> findById(ReferenceType referenceType, String referenceId, String id);

    Maybe<User> findById(String id);

    Single<User> create(ReferenceType referenceType, String referenceId, NewUser newUser, io.gravitee.am.identityprovider.api.User principal);

    Single<User> create(String domain, NewUser newUser, io.gravitee.am.identityprovider.api.User principal);

    Single<User> update(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal);

    Single<User> update(String domain, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal);

    Single<User> updateStatus(ReferenceType referenceType, String referenceId, String id, boolean status, io.gravitee.am.identityprovider.api.User principal);

    Single<User> updateStatus(String domain, String id, boolean status, io.gravitee.am.identityprovider.api.User principal);

    Completable delete(ReferenceType referenceType, String referenceId, String userId, io.gravitee.am.identityprovider.api.User principal);

    Completable resetPassword(ReferenceType referenceType, String referenceId, String userId, String password, io.gravitee.am.identityprovider.api.User principal);

    Completable resetPassword(String domain, String userId, String password, io.gravitee.am.identityprovider.api.User principal);

    Completable sendRegistrationConfirmation(String domain, String userId, io.gravitee.am.identityprovider.api.User principal);

    Completable unlock(ReferenceType referenceType, String referenceId, String userId, io.gravitee.am.identityprovider.api.User principal);

    Single<User> assignRoles(ReferenceType referenceType, String referenceId, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal);

    Single<User> revokeRoles(ReferenceType referenceType, String referenceId, String userId, List<String> roles, io.gravitee.am.identityprovider.api.User principal);

    Single<User> enrollFactors(String userId, List<EnrolledFactor> factors, io.gravitee.am.identityprovider.api.User principal);

    default Single<User> create(String domain, NewUser newUser) {
        return create(domain, newUser, null);
    }

    default Single<User> update(String domain, String id, UpdateUser updateUser) {
        return update(domain, id, updateUser, null);
    }

    default Single<User> updateStatus(String domain, String userId, boolean status) {
        return updateStatus(domain, userId, status, null);
    }

    default Completable delete(ReferenceType referenceType, String referenceId, String userId) {
        return delete(referenceType, referenceId, userId, null);
    }

    default Completable resetPassword(String domain, String userId, String password) {
        return resetPassword(domain, userId, password, null);
    }

    default Completable unlock(ReferenceType referenceType, String referenceId, String userId) {
        return unlock(referenceType, referenceId, userId, null);
    }

    default Single<User> assignRoles(ReferenceType referenceType, String referenceId, String userId, List<String> roles) {
        return assignRoles(referenceType, referenceId, userId, roles, null);
    }

    default Single<User> revokeRoles(ReferenceType referenceType, String referenceId, String userId, List<String> roles) {
        return revokeRoles(referenceType, referenceId, userId, roles, null);
    }

    default Single<User> enrollFactors(String userId, List<EnrolledFactor> factors) {
        return enrollFactors(userId, factors, null);
    }

}
