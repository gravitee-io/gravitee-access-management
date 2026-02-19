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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AccessTokenRepository extends ExpiredDataSweeper {
    /**
     * Find access token by id
     *
     * @param token access token's id
     * @return Access token if any
     */
    Maybe<AccessToken> findByToken(String token);

    /**
     * Create an access token
     *
     * @param accessToken access token to store
     * @return th created access token
     */
    Single<AccessToken> create(AccessToken accessToken);

    /**
     * Delete token by its id
     *
     * @param token token's id
     * @return acknowledge of the operation
     */
    Completable delete(String token);

    /**
     * Retrieve access tokens stored against the provided client id.
     *
     * @param clientId the client id to search
     * @param subject  the end-user technical identifier
     * @return a collection of access tokens
     */
    Observable<AccessToken> findByClientIdAndSubject(String clientId, String subject);

    /**
     * Retrieve access tokens stored against the provided client id.
     *
     * @param clientId the client id to search
     * @return a collection of access tokens
     */
    Observable<AccessToken> findByClientId(String clientId);

    /**
     * Retrieve access tokens stored against the provided authorization code.
     *
     * @param authorizationCode the authorization code to search
     * @return a collection of access tokens
     */
    Observable<AccessToken> findByAuthorizationCode(String authorizationCode);

    /**
     * Delete access tokens by user id
     *
     * @param userId end-user
     * @return acknowledge of the operation
     */
    Completable deleteByUserId(String userId);

    /**
     * Delete access token by domainId, clientId and userId.
     */
    Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId);

    Completable deleteByDomainIdAndUserId(String domainId, UserId userId);

    Completable deleteByDomainIdAndClientId(String domainId, String clientId);


}
