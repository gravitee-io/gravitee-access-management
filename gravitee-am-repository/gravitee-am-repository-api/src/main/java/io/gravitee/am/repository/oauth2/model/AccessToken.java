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

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Setter
@Getter
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

    /**
     * RFC 8693 Token Exchange - Actor claim for delegation scenarios.
     * Contains information about the acting party when this token was obtained through token exchange.
     * See <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.1">RFC 8693 Section 4.1</a>
     */
    private Map<String, Object> actor;

    /**
     * RFC 8693 Token Exchange - The type of the source token used in the exchange.
     * Stored for audit purposes.
     */
    private String sourceTokenType;

    /**
     * RFC 8693 Token Exchange - The ID of the source token used in the exchange.
     * Stored for audit purposes and potential token revocation propagation.
     */
    private String sourceTokenId;

    /**
     * RFC 8693 Token Exchange - The issued token type URI.
     * Indicates the type of token that was issued (e.g., urn:ietf:params:oauth:token-type:access_token).
     */
    private String issuedTokenType;

}
