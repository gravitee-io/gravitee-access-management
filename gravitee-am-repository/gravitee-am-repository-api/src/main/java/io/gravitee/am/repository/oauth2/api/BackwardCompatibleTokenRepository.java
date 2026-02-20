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
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BackwardCompatibleTokenRepository implements TokenRepository {
    private final TokenRepository tokenRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private final boolean maintainLegacyTokenRepositories;

    @Override
    public Maybe<RefreshToken> findRefreshTokenByJti(String jti) {
        return tokenRepository.findRefreshTokenByJti(jti)
                .switchIfEmpty(maintainLegacyTokenRepositories ? refreshTokenRepository.findByToken(jti) : Maybe.empty());
    }

    @Override
    public Single<RefreshToken> create(RefreshToken refreshToken) {
        return tokenRepository.create(refreshToken);
    }

    @Override
    public Maybe<AccessToken> findAccessTokenByJti(String jti) {
        return tokenRepository.findAccessTokenByJti(jti)
                .switchIfEmpty(maintainLegacyTokenRepositories ? accessTokenRepository.findByToken(jti) : Maybe.empty());
    }

    @Override
    public Single<AccessToken> create(AccessToken accessToken) {
        return tokenRepository.create(accessToken);
    }

    @Override
    public Observable<AccessToken> findAccessTokenByAuthorizationCode(String authorizationCode) {
        return tokenRepository.findAccessTokenByAuthorizationCode(authorizationCode)
                .switchIfEmpty(maintainLegacyTokenRepositories ? accessTokenRepository.findByAuthorizationCode(authorizationCode) : Observable.empty());
    }

    @Override
    public Completable deleteByJti(String jti) {
        Completable delete = tokenRepository.deleteByJti(jti);
        if(maintainLegacyTokenRepositories){
            delete = delete.andThen(accessTokenRepository.delete(jti)).andThen(refreshTokenRepository.delete(jti));
        }
        return delete;
    }

    @Override
    public Completable deleteByUserId(String userId) {
        Completable delete = tokenRepository.deleteByUserId(userId);
        if(maintainLegacyTokenRepositories){
            delete = delete.andThen(accessTokenRepository.deleteByUserId(userId))
                    .andThen(refreshTokenRepository.deleteByUserId(userId));
        }
        return delete;
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId) {
        Completable delete = tokenRepository.deleteByDomainIdClientIdAndUserId(domainId, clientId, userId);
        if(maintainLegacyTokenRepositories){
            delete = delete.andThen(accessTokenRepository.deleteByDomainIdClientIdAndUserId(domainId, clientId, userId))
                    .andThen(refreshTokenRepository.deleteByDomainIdClientIdAndUserId(domainId, clientId, userId));
        }
        return delete;
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, UserId userId) {
        Completable delete = tokenRepository.deleteByDomainIdAndUserId(domainId, userId);
        if(maintainLegacyTokenRepositories){
            delete = delete.andThen(accessTokenRepository.deleteByDomainIdAndUserId(domainId, userId))
                    .andThen(refreshTokenRepository.deleteByDomainIdAndUserId(domainId, userId));
        }
        return delete;
    }

    @Override
    public Completable deleteByDomainIdAndClientId(String domainId, String clientId) {
        Completable delete = tokenRepository.deleteByDomainIdAndClientId(domainId, clientId);
        if(maintainLegacyTokenRepositories){
            delete = delete.andThen(accessTokenRepository.deleteByDomainIdAndClientId(domainId, clientId))
                    .andThen(refreshTokenRepository.deleteByDomainIdAndClientId(domainId, clientId));
        }
        return delete;
    }

    @Override
    public Completable purgeExpiredData() {
        return tokenRepository.purgeExpiredData();
    }
}
