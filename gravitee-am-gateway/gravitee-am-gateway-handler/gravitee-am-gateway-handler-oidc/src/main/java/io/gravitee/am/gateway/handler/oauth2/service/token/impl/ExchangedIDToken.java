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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.jackson.ExchangedIDTokenSerializer;

/**
 * Represents an ID token returned from Token Exchange (RFC 8693).
 * <p>
 * Per RFC 8693 Section 2.2.1, when the requested_token_type is ID_TOKEN,
 * the ID token is returned in the access_token field with token_type set to "N_A".
 * <p>
 * This class differs from {@link AccessToken} in that:
 * <ul>
 *   <li>It has no confirmation method (cnf) - not applicable for ID tokens</li>
 *   <li>It does not include scope in the response - ID tokens are for identity, not authorization</li>
 *   <li>It sets token_type to "N_A" by default per RFC 8693</li>
 *   <li>It sets issued_token_type to ID_TOKEN by default</li>
 * </ul>
 *
 * @author GraviteeSource Team
 */
@JsonSerialize(using = ExchangedIDTokenSerializer.class)
public class ExchangedIDToken extends Token {

    /**
     * Creates an ExchangedIDToken with RFC 8693 compliant defaults.
     *
     * @param idTokenString the encoded ID token JWT string
     */
    public ExchangedIDToken(String idTokenString) {
        super(idTokenString);
        // Per RFC 8693, token_type is "N_A" for non-access tokens
        setTokenType("N_A");
        // Set the issued token type to indicate this is an ID token
        setIssuedTokenType(TokenType.ID_TOKEN);
    }
}
