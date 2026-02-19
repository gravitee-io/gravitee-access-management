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

    Completable deleteByJti(String jti);
    Completable deleteByUserId(String userId);
    Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId);
    Completable deleteByDomainIdAndUserId(String domainId, UserId userId);
    Completable deleteByDomainIdAndClientId(String domainId, String clientId);

    enum TokenType {
        ACCESS_TOKEN, REFRESH_TOKEN
    }
}
