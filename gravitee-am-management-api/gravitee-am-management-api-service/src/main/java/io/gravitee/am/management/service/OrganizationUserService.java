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

import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.model.NewAccountAccessToken;
import io.gravitee.am.service.model.NewOrganizationUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface OrganizationUserService {

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size);

    Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria filterCriteria, int page, int size);

    Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size);

    Single<User> findById(ReferenceType referenceType, String referenceId, String id);

    Single<User> update(ReferenceType referenceType, String referenceId, String id, UpdateUser updateUser, io.gravitee.am.identityprovider.api.User principal);

    Single<User> updateStatus(ReferenceType referenceType, String referenceId, String id, boolean status, io.gravitee.am.identityprovider.api.User principal);

    Single<User> createOrUpdate(ReferenceType referenceType, String referenceId, NewOrganizationUser newUser);

    Single<User> createGraviteeUser(Organization organization, NewOrganizationUser newUser, io.gravitee.am.identityprovider.api.User principal);

    Completable resetPassword(String organizationId, User user, String password, io.gravitee.am.identityprovider.api.User principal);

    Single<User> updateLogoutDate(ReferenceType referenceType, String referenceId, String id);

    Flowable<AccountAccessToken> findAccountAccessTokens(String organizationId, String userId);

    Single<AccountAccessToken> createAccountAccessToken(String organizationId, String userId, NewAccountAccessToken newAccountAccessToken, io.gravitee.am.identityprovider.api.User principal);

    Single<User> findByAccessToken(String tokenId, String tokenValue);

    Maybe<AccountAccessToken> revokeToken(String organizationId, String userId, String tokenId, io.gravitee.am.identityprovider.api.User authenticatedUser);
    Single<User> updateStatus(String organizationId, String id, boolean status, io.gravitee.am.identityprovider.api.User principal);


    default Single<User> delete(ReferenceType referenceType, String referenceId, String userId) {
        return delete(referenceType, referenceId, userId, null);
    }

    Single<User> delete(ReferenceType referenceType, String referenceId, String userId, io.gravitee.am.identityprovider.api.User principal);

    Single<User> updateUsername(ReferenceType referenceType, String referenceId, String id, String username, io.gravitee.am.identityprovider.api.User principal);
}
