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
package io.gravitee.am.repository.oauth2.model;

import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccessToken extends Token {

    /**
     * Refresh token issued with the access token
     */
    private String refreshToken;

    /**
     * The authorization code used to obtain the access token
     * Needed for token revocation if authorization code has been used more than once
     * https://tools.ietf.org/html/rfc6749#section-4.1.2
     */
    private String authorizationCode;

    /**
     * Confirmation method https://datatracker.ietf.org/doc/html/rfc8705#section-3.1
     * This attribute isn't persisted, it is only here to allow insertion into the Introspection response
     */
    private Map<String, Object> confirmationMethod;

    public Map<String, Object> getConfirmationMethod() {
        return confirmationMethod;
    }

    public void setConfirmationMethod(Map<String, Object> confirmationMethod) {
        this.confirmationMethod = confirmationMethod;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }
}
