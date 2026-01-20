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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.reactivex.rxjava3.core.Single;

/**
 * Interface for validating subject tokens in RFC 8693 Token Exchange.
 *
 * Implementations of this interface are responsible for validating specific
 * token types (Access Token, ID Token, Refresh Token, JWT, ...).
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
public interface TokenValidator {

    /**
     * Validate the given token and extract its claims.
     *
     * @param token the token to validate
     * @param settings the token exchange settings
     * @param domain the domain context
     * @return a Single emitting the validated token with extracted claims
     */
    Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain);

    /**
     * Get the token type URI that this validator supports.
     *
     * @return the token type URI (e.g., "urn:ietf:params:oauth:token-type:access_token")
     */
    String getSupportedTokenType();

    /**
     * Check if this validator can handle the given token type.
     *
     * @param tokenType the token type URI
     * @return true if this validator supports the token type
     */
    default boolean supports(String tokenType) {
        return getSupportedTokenType().equals(tokenType);
    }
}
