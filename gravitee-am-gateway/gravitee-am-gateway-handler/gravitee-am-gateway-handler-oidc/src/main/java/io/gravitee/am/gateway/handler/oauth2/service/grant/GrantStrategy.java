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
package io.gravitee.am.gateway.handler.oauth2.service.grant;

import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;

/**
 * Strategy interface for OAuth2 grant types.
 * Each grant type (authorization_code, client_credentials, token_exchange, etc.)
 * implements this interface to handle its specific flow.
 *
 * @author GraviteeSource Team
 */
public interface GrantStrategy {

    /**
     * Check if this strategy supports the given grant type for the specified client and domain.
     *
     * @param grantType the OAuth2 grant type
     * @param client the OAuth2 client
     * @param domain the domain context
     * @return true if this strategy can handle the grant type
     */
    boolean supports(String grantType, Client client, Domain domain);

    /**
     * Process the token request and return a TokenCreationRequest ready for token generation.
     *
     * @param request the incoming token request
     * @param client the OAuth2 client
     * @param domain the domain context
     * @return a Single emitting the TokenCreationRequest for token generation
     */
    Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain);
}
