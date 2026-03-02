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
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for RFC 8693 OAuth 2.0 Token Exchange.
 * Handles the validation and processing of token exchange requests.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
public class TokenExchangeStrategy implements GrantStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenExchangeStrategy.class);

    private final TokenExchangeService tokenExchangeService;
    private final UserGatewayService userGatewayService;

    public TokenExchangeStrategy(TokenExchangeService tokenExchangeService, UserGatewayService userGatewayService) {
        this.tokenExchangeService = tokenExchangeService;
        this.userGatewayService = userGatewayService;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        if (!GrantType.TOKEN_EXCHANGE.equals(grantType)) {
            return false;
        }

        if (!domain.useTokenExchange()) {
            LOGGER.debug("Token exchange is not enabled for domain: {}", domain.getId());
            return false;
        }

        if (!client.hasGrantType(GrantType.TOKEN_EXCHANGE)) {
            LOGGER.debug("Client {} does not support token exchange grant type", client.getClientId());
            return false;
        }

        return true;
    }

    @Override
    public Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain) {
        LOGGER.debug("Processing token exchange request for client: {}", client.getClientId());

        return tokenExchangeService.exchange(request, client, domain, userGatewayService)
                .doOnSuccess(result -> LOGGER.debug("Token exchange successful for subject: {}", result.user().getId()))
                .map(result -> TokenCreationRequest.forTokenExchange(
                        request,
                        result.user(),
                        result.issuedTokenType(),
                        result.exchangeExpiration(),
                        result.subjectTokenId(),
                        result.subjectTokenType(),
                        result.actorTokenId(),
                        result.actorTokenType(),
                        result.actorInfo()
                ));
    }
}
