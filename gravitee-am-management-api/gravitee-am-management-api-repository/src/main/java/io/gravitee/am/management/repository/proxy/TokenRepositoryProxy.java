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
package io.gravitee.am.management.repository.proxy;

import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2AccessToken;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class TokenRepositoryProxy extends AbstractProxy<TokenRepository> implements TokenRepository {

    @Override
    public Optional<OAuth2Authentication> readAuthentication(OAuth2AccessToken token) {
        return target.readAuthentication(token);
    }

    @Override
    public Optional<OAuth2Authentication> readAuthentication(String token) {
        return target.readAuthentication(token);
    }

    @Override
    public void storeAccessToken(OAuth2AccessToken token, OAuth2Authentication authentication, String authenticationKey) {
        target.storeAccessToken(token, authentication, authenticationKey);
    }

    @Override
    public Optional<OAuth2AccessToken> readAccessToken(String tokenValue) {
        return target.readAccessToken(tokenValue);
    }

    @Override
    public void removeAccessToken(OAuth2AccessToken token) {
        target.removeAccessToken(token);
    }

    @Override
    public void removeAccessToken(String token) {
        target.removeAccessToken(token);
    }

    @Override
    public void storeRefreshToken(OAuth2RefreshToken refreshToken, OAuth2Authentication authentication) {
        target.storeRefreshToken(refreshToken, authentication);
    }

    @Override
    public Optional<OAuth2RefreshToken> readRefreshToken(String tokenValue) {
        return target.readRefreshToken(tokenValue);
    }

    @Override
    public Optional<OAuth2Authentication> readAuthenticationForRefreshToken(OAuth2RefreshToken token) {
        return target.readAuthenticationForRefreshToken(token);
    }

    @Override
    public void removeRefreshToken(OAuth2RefreshToken token) {
        target.removeRefreshToken(token);
    }

    @Override
    public void removeAccessTokenUsingRefreshToken(OAuth2RefreshToken refreshToken) {
        target.removeAccessTokenUsingRefreshToken(refreshToken);
    }

    @Override
    public Optional<OAuth2AccessToken> getAccessToken(String authenticationKey) {
        return target.getAccessToken(authenticationKey);
    }

    @Override
    public Collection<OAuth2AccessToken> findTokensByClientIdAndUserName(String clientId, String userName) {
        return target.findTokensByClientIdAndUserName(clientId, userName);
    }

    @Override
    public Collection<OAuth2AccessToken> findTokensByClientId(String clientId) {
        return target.findTokensByClientId(clientId);
    }
}
