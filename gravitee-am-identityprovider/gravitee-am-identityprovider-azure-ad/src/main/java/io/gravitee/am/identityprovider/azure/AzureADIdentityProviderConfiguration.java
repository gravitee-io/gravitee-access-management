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
package io.gravitee.am.identityprovider.azure;

import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.oidc.jwt.KeyResolver;

import java.util.Set;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AzureADIdentityProviderConfiguration implements OpenIDConnectIdentityProviderConfiguration {

    private static final String DEFAULT_RESPONSE_TYPE = "code";

    public static final String HOST_MICROSOFT_LOGIN = "https://login.microsoftonline.com/";
    public static final String AUTHORIZATION_PATH = "/oauth2/v2.0/authorize";
    public static final String TOKEN_PATH = "/oauth2/v2.0/token";
    public static final String JWKS_PATH = "/discovery/v2.0/keys";

    public static final String CODE_PARAMETER = "code";
    private String tenantId;
    private String clientId;
    private String clientSecret;
    private Set<String> scopes;

    private boolean encodeRedirectUri;
    private Integer connectTimeout = 10000;
    private Integer maxPoolSize = 200;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

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
        return HOST_MICROSOFT_LOGIN + getTenantId() + JWKS_PATH;
    }

    @Override
    public String getUserAuthorizationUri() {
        return HOST_MICROSOFT_LOGIN + getTenantId() + AUTHORIZATION_PATH;
    }

    @Override
    public String getAccessTokenUri() {
        return HOST_MICROSOFT_LOGIN + getTenantId() + TOKEN_PATH;
    }

    @Override
    public String getUserProfileUri() {
        return null;
    }

    @Override
    public String getResponseType() {
        return DEFAULT_RESPONSE_TYPE;
    }
}
