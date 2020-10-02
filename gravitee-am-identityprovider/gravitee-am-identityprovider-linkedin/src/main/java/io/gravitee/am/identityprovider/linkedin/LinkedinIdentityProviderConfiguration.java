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
package io.gravitee.am.identityprovider.linkedin;

import io.gravitee.am.identityprovider.api.social.SocialIdentityProviderConfiguration;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LinkedinIdentityProviderConfiguration implements SocialIdentityProviderConfiguration {

    private String USER_AUTHORIZATION_URI = "https://www.linkedin.com/oauth/v2/authorization";
    private String ACCESS_TOKEN_URI = "https://www.linkedin.com/oauth/v2/accessToken";
    private String USER_PROFILE_URI = "https://api.linkedin.com/v2/me?projection=(*,profilePicture(displayImage~:playableStreams))";
    private String USER_EMAIL_ADDRESS_URI = "https://api.linkedin.com/v2/emailAddress?q=members&projection=(elements*(handle~))";
    private String CODE_PARAMETER = "code";
    private String RESPONSE_TYPE = "code";
    private String clientId;
    private String clientSecret;
    private Set<String> scopes;
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

    public Set<String> getScopes() {
        return scopes;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    public String getUserAuthorizationUri() {
        return USER_AUTHORIZATION_URI;
    }

    public String getAccessTokenUri() {
        return ACCESS_TOKEN_URI;
    }

    public String getUserProfileUri() {
        return USER_PROFILE_URI;
    }

    public String getUserEmailAddressUri() {
        return USER_EMAIL_ADDRESS_URI;
    }

    public String getCodeParameter() {
        return CODE_PARAMETER;
    }

    public String getResponseType() {
        return RESPONSE_TYPE;
    }

    public void setUserAuthorizationUri(String userAuthorizationUri) {
        USER_AUTHORIZATION_URI = userAuthorizationUri;
    }

    public void setAccessTokenUri(String accessTokenUri) {
        ACCESS_TOKEN_URI = accessTokenUri;
    }

    public void setUserProfileUri(String userProfileUri) {
        USER_PROFILE_URI = userProfileUri;
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
}
