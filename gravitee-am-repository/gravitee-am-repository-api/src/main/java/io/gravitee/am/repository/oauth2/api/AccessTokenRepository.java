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

import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.AccessTokenCriteria;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AccessTokenRepository {

    Maybe<AccessToken> findByToken(String token);

    Single<AccessToken> create(AccessToken accessToken);

    Completable delete(String token);

    /**
     * Retrieve access tokens stored against the provided client id.
     *
     * @param clientId the client id to search
     * @param subject the end-user technical identifier
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
     * Count access tokens stored against the provided client id.
     *
     * @param clientId the client id to search
     * @return the number of access tokens
     */
    Single<Long> countByClientId(String clientId);

    /**
     * Find access token by criteria to know if the access token must be re-new or re-use
     *
     * @param accessTokenCriteria
     * @return an access token or empty
     */
    Maybe<AccessToken> findByCriteria(AccessTokenCriteria accessTokenCriteria);
}
