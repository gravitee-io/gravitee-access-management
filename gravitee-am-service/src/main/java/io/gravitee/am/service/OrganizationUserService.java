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
package io.gravitee.am.service;

import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.service.model.NewAccountAccessToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface OrganizationUserService extends CommonUserService {

    /**
     * Set the ORGANIZATION_USER role to a newly create user.
     * @param principal of the user (may be null if creation comes from the Console action, not from a login)
     * @param user on who the default role must be applied
     */
    Completable setRoles(io.gravitee.am.identityprovider.api.User principal, io.gravitee.am.model.User user);

    /**
     * See {@link #setRoles(io.gravitee.am.identityprovider.api.User, User)} with null principal
     * @param user
     * @return
     */
    Completable setRoles(io.gravitee.am.model.User user);

    Flowable<AccountAccessToken> findUserAccessTokens(String organisationId, String userId);

    Single<AccountAccessToken> generateAccountAccessToken(User user, NewAccountAccessToken newAccountToken, String issuer);

    Completable revokeUserAccessTokens(ReferenceType referenceType, String organizationId, String userId);

    Single<User> findByAccessToken(String token, String tokenValue);

    Maybe<AccountAccessToken> revokeToken(String organizationId, String userId, String tokenId);

    Single<User> delete(String userId);
}
