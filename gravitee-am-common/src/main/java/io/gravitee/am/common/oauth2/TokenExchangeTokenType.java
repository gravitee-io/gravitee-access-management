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

import java.util.Optional;

/**
 * Token Type Identifiers for OAuth 2.0 Token Exchange (RFC 8693)
 *
 * See <a href="https://tools.ietf.org/html/rfc8693#section-3">RFC 8693 Section 3 - Token Type Identifiers</a>
 *
 * @author GraviteeSource Team
 */
public enum TokenExchangeTokenType {

    /**
     * Indicates that the token is an OAuth 2.0 access token issued by the given authorization server.
     */
    ACCESS_TOKEN("urn:ietf:params:oauth:token-type:access_token"),

    /**
     * Indicates that the token is an OAuth 2.0 refresh token issued by the given authorization server.
     */
    REFRESH_TOKEN("urn:ietf:params:oauth:token-type:refresh_token"),

    /**
     * Indicates that the token is an ID Token as defined in OpenID Connect Core 1.0.
     */
    ID_TOKEN("urn:ietf:params:oauth:token-type:id_token"),

    /**
     * Indicates that the token is a base64url-encoded SAML 1.1 assertion.
     */
    SAML1("urn:ietf:params:oauth:token-type:saml1"),

    /**
     * Indicates that the token is a base64url-encoded SAML 2.0 assertion.
     */
    SAML2("urn:ietf:params:oauth:token-type:saml2"),

    /**
     * Indicates that the token is a JWT.
     */
    JWT("urn:ietf:params:oauth:token-type:jwt");

    private final String value;

    TokenExchangeTokenType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse a token type URI string into the corresponding enum value.
     *
     * @param value the token type URI
     * @return the corresponding TokenExchangeTokenType
     * @throws IllegalArgumentException if the value doesn't match any known token type
     */
    public static TokenExchangeTokenType fromValue(String value) {
        for (TokenExchangeTokenType tokenType : values()) {
            if (tokenType.value.equals(value)) {
                return tokenType;
            }
        }
        throw new IllegalArgumentException("Unknown token type: " + value);
    }

    /**
     * Try to parse a token type URI string into the corresponding enum value.
     *
     * @param value the token type URI
     * @return Optional containing the TokenExchangeTokenType if found, empty otherwise
     */
    public static Optional<TokenExchangeTokenType> fromValueOptional(String value) {
        try {
            return Optional.of(fromValue(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Check if this token type is supported for token exchange as a subject token.
     * For AM-issued tokens, we support access_token, refresh_token, and id_token.
     *
     * @return true if this token type can be used as a subject token
     */
    public boolean isSupportedAsSubjectToken() {
        return this == ACCESS_TOKEN || this == REFRESH_TOKEN || this == ID_TOKEN;
    }

    /**
     * Check if this token type is supported for token exchange as an actor token.
     * For AM-issued tokens, we support access_token.
     *
     * @return true if this token type can be used as an actor token
     */
    public boolean isSupportedAsActorToken() {
        return this == ACCESS_TOKEN;
    }
}
