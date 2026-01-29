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
package io.gravitee.am.identityprovider.twitter;

import io.gravitee.am.identityprovider.api.social.ProviderResponseType;
import io.gravitee.am.identityprovider.api.social.SocialIdentityProviderConfiguration;
import io.gravitee.secrets.api.annotation.Secret;

import java.util.Collections;
import java.util.Set;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TwitterIdentityProviderConfiguration implements SocialIdentityProviderConfiguration {
    private static final String BASE_URL = "https://api.twitter.com/";
    private static final String ACCESS_TOKEN_URL = BASE_URL + "oauth/access_token";
    private static final String USER_PROFILE_URL = BASE_URL + "1.1/account/verify_credentials.json";
    private static final String REQUEST_TOKEN_URL = BASE_URL + "oauth/request_token";
    private static final String AUTHORIZE_URL = BASE_URL + "oauth/authorize";
    private static final String TOKEN_PARAMETER = "oauth_token";
    private static final String VERIFIER_PARAMETER = "oauth_verifier";
    private String clientId;
    @Secret
    private String clientSecret;

    private boolean encodeRedirectUri;
    private Integer connectTimeout = 10000;
    private Integer idleTimeout = 10000;
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

    public String getUserAuthorizationUri() {
        return AUTHORIZE_URL;
    }

    public String getRequestTokenUrl() {
        return REQUEST_TOKEN_URL;
    }

    public String getAccessTokenUri() {
        return ACCESS_TOKEN_URL;
    }

    public String getUserProfileUri() {
        return USER_PROFILE_URL;
    }

    public String getCodeParameter() {
        return TOKEN_PARAMETER;
    }

    public String getTokenVerifier() {
        return VERIFIER_PARAMETER;
    }

    @Override
    public Set<String> getScopes() {
        return Collections.emptySet();
    }

    @Override
    public ProviderResponseType getProviderResponseType() {
        return ProviderResponseType.CODE;
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

    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Integer idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    @Override
    public String getLogoutUri() {
        return null;
    }

}
