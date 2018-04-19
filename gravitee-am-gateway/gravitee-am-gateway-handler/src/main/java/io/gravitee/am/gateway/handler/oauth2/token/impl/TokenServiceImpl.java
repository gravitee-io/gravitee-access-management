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
package io.gravitee.am.gateway.handler.oauth2.token.impl;

import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.request.OAuth2Request;
import io.gravitee.common.utils.UUID;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenServiceImpl implements TokenService {

    private int accessTokenValiditySeconds = 60 * 60 * 12; // default 12 hours.

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Override
    public Maybe<AccessToken> get(String accessToken) {
        final Maybe<io.gravitee.am.repository.oauth2.model.AccessToken> result = accessTokenRepository.findByToken(accessToken).cache();
        return result
                .isEmpty()
                .flatMapMaybe(empty -> (empty) ? Maybe.empty() : result.map(this::convert));
    }

    @Override
    public Single<AccessToken> create(OAuth2Authentication authentication) {
        // TODO manage refresh token
        // TODO manage token enhancer
        // TODO check if access token already exists, is it expired ? delete/renew
        io.gravitee.am.repository.oauth2.model.AccessToken accessToken = new io.gravitee.am.repository.oauth2.model.AccessToken();

        accessToken.setId(UUID.random().toString());
        accessToken.setToken(UUID.random().toString());
        accessToken.setClientId(authentication.getOAuth2Request().getClientId());
        accessToken.setSubject(((User) authentication.getUserAuthentication().getPrincipal()).getId());
        int validitySeconds = getAccessTokenValiditySeconds(authentication.getOAuth2Request());
        if (validitySeconds > 0) {
            accessToken.setExpireAt(new Date(System.currentTimeMillis() + (validitySeconds * 1000L)));
        }
        accessToken.setCreatedAt(new Date());
        accessToken.setRefreshToken(null);
        accessToken.setScope(authentication.getOAuth2Request().getScope());

        return accessTokenRepository.create(accessToken).map(this::convert);
    }

    @Override
    public Single<AccessToken> refresh() {
        return null;
    }

    private Integer getAccessTokenValiditySeconds(OAuth2Request oAuth2Request) {
        // TODO manage client options
        return accessTokenValiditySeconds;
    }

    private AccessToken convert(io.gravitee.am.repository.oauth2.model.AccessToken accessToken) {
        if (accessToken == null) {
            return null;
        }

        DefaultAccessToken token = new DefaultAccessToken(accessToken.getToken());
        if (accessToken.getScope() != null && !accessToken.getScope().isEmpty()) {
            token.setScope(String.join(" ", accessToken.getScope()));
        }

        token.setExpiresIn(accessToken.getExpireAt() != null ?
                Long.valueOf((accessToken.getExpireAt().getTime() - System.currentTimeMillis()) / 1000L).intValue() : 0);

        return token;
    }
}
