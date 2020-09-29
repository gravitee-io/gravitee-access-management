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
package io.gravitee.am.identityprovider.twitter.authentication.utils;

import io.gravitee.am.identityprovider.twitter.TwitterIdentityProviderConfiguration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuthCredentials {
    private String clientId;
    private String clientSecret;
    private String token;
    private String tokenSecret;

    public OAuthCredentials(TwitterIdentityProviderConfiguration config) {
        this(config, null, null);
    }

    public OAuthCredentials(TwitterIdentityProviderConfiguration config, String token, String tokenSecret) {
        this.clientId = config.getClientId();
        this.clientSecret = config.getClientSecret();
        this.token = token;
        this.tokenSecret = tokenSecret;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getToken() {
        return token;
    }

    public String getTokenSecret() {
        return tokenSecret;
    }
}
