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
package io.gravitee.am.gateway.handler.oauth2.service.grant.impl;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for OAuth 2.0 Client Credentials Grant.
 * Used when the client is acting on its own behalf (no user).
 *
 * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.4">RFC 6749 Section 4.4</a>
 * @author GraviteeSource Team
 */
public class ClientCredentialsStrategy implements GrantStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCredentialsStrategy.class);

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        if (!GrantType.CLIENT_CREDENTIALS.equals(grantType)) {
            return false;
        }

        if (!client.hasGrantType(GrantType.CLIENT_CREDENTIALS)) {
            LOGGER.debug("Client {} does not support client_credentials grant type", client.getClientId());
            return false;
        }

        return true;
    }

    @Override
    public Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain) {
        LOGGER.debug("Processing client credentials request for client: {}", client.getClientId());

        // Client credentials does NOT support refresh tokens per RFC 6749 Section 4.4.3
        // "A refresh token SHOULD NOT be included"
        return Single.just(TokenCreationRequest.forClientCredentials(request, false));
    }
}
