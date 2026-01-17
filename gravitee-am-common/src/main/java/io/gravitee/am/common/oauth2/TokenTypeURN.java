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
package io.gravitee.am.common.oauth2;

import java.util.Arrays;
import java.util.List;

/**
 * RFC 8693 Token Type Identifiers
 *
 * See <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3 - Token Type Identifiers</a>
 *
 * @author GraviteeSource Team
 */
public interface TokenTypeURN {

    /**
     * Indicates that the token is an OAuth 2.0 access token issued by the given authorization server.
     */
    String ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";

    /**
     * Indicates that the token is an OAuth 2.0 refresh token issued by the given authorization server.
     */
    String REFRESH_TOKEN = "urn:ietf:params:oauth:token-type:refresh_token";

    /**
     * Indicates that the token is an ID Token as defined in Section 2 of OpenID Connect Core 1.0.
     */
    String ID_TOKEN = "urn:ietf:params:oauth:token-type:id_token";

    /**
     * Indicates that the token is a base64url-encoded SAML 1.1 assertion.
     */
    String SAML1 = "urn:ietf:params:oauth:token-type:saml1";

    /**
     * Indicates that the token is a base64url-encoded SAML 2.0 assertion.
     */
    String SAML2 = "urn:ietf:params:oauth:token-type:saml2";

    /**
     * Indicates that the token is a JWT.
     */
    String JWT = "urn:ietf:params:oauth:token-type:jwt";

    /**
     * List of all supported token types.
     */
    List<String> ALL_TYPES = Arrays.asList(ACCESS_TOKEN, REFRESH_TOKEN, ID_TOKEN, SAML1, SAML2, JWT);

    /**
     * Default token types supported for subject_token.
     */
    List<String> DEFAULT_SUBJECT_TOKEN_TYPES = Arrays.asList(ACCESS_TOKEN, ID_TOKEN, JWT, REFRESH_TOKEN);

    /**
     * Default token types supported for actor_token.
     */
    List<String> DEFAULT_ACTOR_TOKEN_TYPES = Arrays.asList(ACCESS_TOKEN, JWT);

    /**
     * Default token types that can be requested.
     */
    List<String> DEFAULT_REQUESTED_TOKEN_TYPES = Arrays.asList(ACCESS_TOKEN, JWT);

    /**
     * Check if the given token type is a valid RFC 8693 token type.
     *
     * @param tokenType the token type to check
     * @return true if valid, false otherwise
     */
    static boolean isValid(String tokenType) {
        return tokenType != null && ALL_TYPES.contains(tokenType);
    }
}
