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
import io.gravitee.am.gateway.handler.oauth2.token.AuthenticationKeyGenerator;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2AccessToken;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.request.OAuth2Request;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.UUID;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenServiceImpl implements TokenService {

    private int accessTokenValiditySeconds = 60 * 60 * 12; // default 12 hours.

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private AuthenticationKeyGenerator authenticationKeyGenerator;

    @Override
    public Maybe<AccessToken> get(String accessToken) {
        return tokenRepository
                .getAccessToken(accessToken)
                .map(this::convert);
    }

    @Override
    public Single<AccessToken> create(OAuth2Authentication oAuth2Authentication) {
        String authenticationKey = authenticationKeyGenerator.extractKey(oAuth2Authentication);
        OAuth2AccessToken oAuth2AccessToken = createAccessToken(oAuth2Authentication);
        return tokenRepository.storeAccessToken(oAuth2AccessToken, oAuth2Authentication, authenticationKey)
                .map(this::convert);
    }

    @Override
    public Single<AccessToken> refresh() {
        return null;
    }

    private OAuth2AccessToken createAccessToken(OAuth2Authentication authentication) {
        // TODO manage refresh token
        // TODO manage token enhancer
        // TODO check if access token already exists, is it expired ? delete/renew
        OAuth2AccessToken token = new OAuth2AccessToken(UUID.randomUUID().toString());
        int validitySeconds = getAccessTokenValiditySeconds(authentication.getOAuth2Request());
        if (validitySeconds > 0) {
            token.setExpiration(new Date(System.currentTimeMillis() + (validitySeconds * 1000L)));
        }
        token.setRefreshToken(null);
        token.setScope(authentication.getOAuth2Request().getScope());
        return token;
    }

    private Integer getAccessTokenValiditySeconds(OAuth2Request oAuth2Request) {
        // TODO manage client options
        return accessTokenValiditySeconds;
    }

    private AccessToken convert(OAuth2AccessToken oAuth2AccessToken) {
        DefaultAccessToken accessToken = new DefaultAccessToken(oAuth2AccessToken.getValue());
        accessToken.setTokenType(oAuth2AccessToken.getTokenType());
        accessToken.setExpiresIn(oAuth2AccessToken.getExpiration() != null ?
                Long.valueOf((oAuth2AccessToken.getExpiration().getTime() - System.currentTimeMillis()) / 1000L).intValue() : 0);
        if (oAuth2AccessToken.getRefreshToken() != null) {
            accessToken.setRefreshToken(oAuth2AccessToken.getRefreshToken().getValue());
        }
        if (oAuth2AccessToken.getScope() != null && !oAuth2AccessToken.getScope().isEmpty()) {
            accessToken.setScope(String.join(" ", oAuth2AccessToken.getScope()));
        }
        return accessToken;
    }
}
