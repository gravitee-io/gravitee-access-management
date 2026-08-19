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
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.*;

public interface TokenRepository extends ExpiredDataSweeper {

    Maybe<RefreshToken> findRefreshTokenByJti(String jti);
    Single<RefreshToken> create(RefreshToken refreshToken);

    Maybe<AccessToken> findAccessTokenByJti(String jti);
    Single<AccessToken> create(AccessToken accessToken);
    Observable<AccessToken> findAccessTokenByAuthorizationCode(String authorizationCode);

    /**
     * Store an access token and, when provided, its refresh token in a single write.
     * <p>
     * Not atomic on every backend: JDBC issues one statement so a failure stores neither token,
     * while MongoDB stops at the failing document but keeps the ones already written. On error,
     * callers must assume that one of the tokens may have been stored.
     *
     * @param accessToken the access token to store
     * @param refreshToken the refresh token to store, may be null
     * @return acknowledge of the operation
     */
    Completable create(AccessToken accessToken, RefreshToken refreshToken);

    Completable deleteByJti(String jti);
    Completable deleteByUserId(String userId);
    Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId);
    Completable deleteByDomainIdAndUserId(String domainId, UserId userId);
    Completable deleteByDomainIdAndClientId(String domainId, String clientId);

    enum TokenType {
        ACCESS_TOKEN, REFRESH_TOKEN
    }
}
