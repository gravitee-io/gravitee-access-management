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
package io.gravitee.am.gateway.handler.oauth2.service.granter.tokenexchange;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.service.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeResult;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.Response;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of RFC 8693 OAuth 2.0 Token Exchange.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
public class TokenExchangeGranter extends AbstractTokenGranter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenExchangeGranter.class);

    private final Domain domain;
    private final TokenExchangeService tokenExchangeService;

    public TokenExchangeGranter(TokenRequestResolver tokenRequestResolver,
                                TokenService tokenService,
                                TokenExchangeService tokenExchangeService,
                                Domain domain,
                                RulesEngine rulesEngine) {
        super(GrantType.TOKEN_EXCHANGE);
        setTokenRequestResolver(tokenRequestResolver);
        setTokenService(tokenService);
        setRulesEngine(rulesEngine);
        this.domain = domain;
        this.tokenExchangeService = tokenExchangeService;
        setSupportRefreshToken(false);
    }

    @Override
    public boolean handle(String grantType, Client client) {
        if (!domain.useTokenExchange()) {
            LOGGER.debug("Token exchange is not enabled for domain: {}", domain.getId());
            return false;
        }
        return super.handle(grantType, client);
    }

    @Override
    public Single<Token> grant(TokenRequest tokenRequest, Client client) {
        return executeTokenExchange(tokenRequest, null, client);
    }

    @Override
    public Single<Token> grant(TokenRequest tokenRequest, Response response, Client client) {
        return executeTokenExchange(tokenRequest, response, client);
    }

    private Single<Token> executeTokenExchange(TokenRequest tokenRequest, Response response, Client client) {
        return super.parseRequest(tokenRequest, client)
                .flatMap(tr -> tokenExchangeService.exchange(tr, client, domain)
                        .doOnSuccess(result -> LOGGER.debug("Token exchange successful for subject: {}", result.user().getId()))
                        .flatMap(result -> handleTokenExchange(tr, response, client, result)));
    }

    private Single<Token> handleTokenExchange(TokenRequest tokenRequest, Response response,
                                              Client client, TokenExchangeResult exchangeResult) {
        User user = exchangeResult.user();

        OAuth2Request oAuth2Request = createOAuth2Request(tokenRequest, user, exchangeResult);

        return getRulesEngine().fire(ExtensionPoint.PRE_TOKEN, oAuth2Request, response, client, user)
                .map(executionContext -> {
                    oAuth2Request.getExecutionContext().putAll(executionContext.getAttributes());
                    return oAuth2Request;
                })
                .flatMap(req -> getTokenService().create(req, client, user))
                .flatMap(token -> getRulesEngine().fire(ExtensionPoint.POST_TOKEN, oAuth2Request, client, user)
                        .map(executionContext -> token));
    }

    private OAuth2Request createOAuth2Request(TokenRequest tokenRequest, User user, TokenExchangeResult exchangeResult) {
        OAuth2Request oAuth2Request = tokenRequest.createOAuth2Request();
        oAuth2Request.setSubject(user.getId());
        oAuth2Request.setSupportRefreshToken(false);

        // Set token exchange specific fields
        oAuth2Request.setIssuedTokenType(exchangeResult.issuedTokenType());
        oAuth2Request.setExchangeExpiration(exchangeResult.exchangeExpiration());

        return oAuth2Request;
    }
}
