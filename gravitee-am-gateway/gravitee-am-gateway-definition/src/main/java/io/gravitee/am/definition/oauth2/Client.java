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
package io.gravitee.am.definition.oauth2;

import java.util.Arrays;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Client {

    private final static int DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS = 7200;
    private final static int DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS = 14400;

    private final static List<GrantType> AUTHORIZED_GRANT_TYPES = Arrays.asList(
            GrantType.AUTHORIZATION_CODE, GrantType.CLIENT_CREDENTIALS, GrantType.IMPLICIT,
            GrantType.PASSWORD, GrantType.REFRESH_TOKEN);

    private String clientId;

    private String clientSecret;

    private List<String> redirectUris;

    private List<GrantType> authorizedGrantTypes = AUTHORIZED_GRANT_TYPES;

    private List<String> scopes;

    private int accessTokenValiditySeconds = DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS;

    private int refreshTokenValiditySeconds = DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public List<GrantType> getAuthorizedGrantTypes() {
        return authorizedGrantTypes;
    }

    public void setAuthorizedGrantTypes(List<GrantType> authorizedGrantTypes) {
        this.authorizedGrantTypes = authorizedGrantTypes;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public int getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    public void setAccessTokenValiditySeconds(int accessTokenValiditySeconds) {
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    }

    public int getRefreshTokenValiditySeconds() {
        return refreshTokenValiditySeconds;
    }

    public void setRefreshTokenValiditySeconds(int refreshTokenValiditySeconds) {
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }
}
