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
package io.gravitee.am.identityprovider.google;

import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.oidc.jwt.KeyResolver;

import java.util.Set;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GoogleIdentityProviderConfiguration implements OpenIDConnectIdentityProviderConfiguration {

    private static final String DEFAULT_RESPONSE_TYPE = "code";

    public static final String CODE_PARAMETER = "code";

    public static final String JWKS_RESOLVER_URL = "https://www.googleapis.com/oauth2/v3/certs";
    public static final String AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    public static final String USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    private String clientId;
    private String clientSecret;
    private Set<String> scopes;

    private boolean encodeRedirectUri;
    private Integer connectTimeout = 10000;
    private Integer maxPoolSize = 200;

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

    public String getCodeParameter() {
        return CODE_PARAMETER;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    public boolean isEncodeRedirectUri() {
        return encodeRedirectUri;
    }

    public void setEncodeRedirectUri(boolean encodeRedirectUri) {
        this.encodeRedirectUri = encodeRedirectUri;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    @Override
    public String getWellKnownUri() {
        return null;
    }

    @Override
    public boolean isUseIdTokenForUserInfo() {
        return true;
    }

    @Override
    public KeyResolver getPublicKeyResolver() {
        return KeyResolver.JWKS_URL;
    }

    @Override
    public SignatureAlgorithm getSignatureAlgorithm() {
        return SignatureAlgorithm.RS256;
    }

    @Override
    public String getResolverParameter() {
        return JWKS_RESOLVER_URL;
    }

    @Override
    public String getUserAuthorizationUri() {
        return AUTHORIZATION_URL;
    }

    @Override
    public String getAccessTokenUri() {
        return TOKEN_URL;
    }

    @Override
    public String getUserProfileUri() {
        return USER_INFO_URL;
    }

    @Override
    public String getResponseType() {
        return DEFAULT_RESPONSE_TYPE;
    }
}
