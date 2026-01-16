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
package io.gravitee.am.extensiongrant.tokenexchange.validation;

import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.reactivex.rxjava3.core.Single;

/**
 * Interface for validating subject tokens in RFC 8693 Token Exchange.
 *
 * Implementations of this interface are responsible for validating specific
 * token types (JWT, SAML, Access Token, etc.) and extracting claims from them.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
public interface SubjectTokenValidator {

    /**
     * Validate the given token and extract its claims.
     *
     * @param token the token to validate
     * @param configuration the extension grant configuration
     * @return a Single emitting the validated token with extracted claims
     * @throws InvalidGrantException if the token is invalid
     */
    Single<ValidatedToken> validate(String token, TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException;

    /**
     * Get the token type URI that this validator supports.
     *
     * @return the token type URI (e.g., "urn:ietf:params:oauth:token-type:jwt")
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
