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

import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;

/**
 * Service for handling RFC 8693 OAuth 2.0 Token Exchange operations.
 *
 * This service is responsible for:
 * - Validating token exchange requests
 * - Validating subject tokens
 * - Returning exchange result with user and token metadata
 *
 * This implementation supports impersonation only (no delegation/actor support).
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
public interface TokenExchangeService {

    /**
     * Perform a token exchange operation.
     * Validates the request and subject token, modifies the tokenRequest with scopes,
     * and returns exchange result containing user and token metadata.
     *
     * @param tokenRequest the token request containing exchange parameters (will be modified with scopes)
     * @param client the client performing the exchange
     * @param domain the domain context
     * @return exchange result with user and token metadata
     */
    Single<TokenExchangeResult> exchange(TokenRequest tokenRequest, Client client, Domain domain);
}
