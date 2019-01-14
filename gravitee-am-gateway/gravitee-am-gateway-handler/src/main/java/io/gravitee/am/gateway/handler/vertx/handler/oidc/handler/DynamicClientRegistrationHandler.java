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
package io.gravitee.am.gateway.handler.vertx.handler.oidc.handler;

import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oidc.exception.ClientRegistrationForbiddenException;
import io.gravitee.am.model.Domain;
import io.reactivex.Maybe;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;

/**
 * Dynamic Client Registration is a protocol that allows OAuth client applications to register with an OAuth server.
 * Specifications are defined by OpenID Foundation and by the IETF as RFC 7591 too.
 * They define how a client may submit a request to register itself and how should be the response.
 *
 * See <a href="https://openid.net/specs/openid-connect-registration-1_0.html">Openid Connect Dynamic Client Registration</a>
 * See <a href="https://tools.ietf.org/html/rfc7591"> OAuth 2.0 Dynamic Client Registration Protocol</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DynamicClientRegistrationHandler extends AbstractProtectedHandler {

    private TokenService tokenService;
    private ClientSyncService clientSyncService;
    private JwtService jwtService;
    private Domain domain;

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicClientRegistrationHandler.class);

    public DynamicClientRegistrationHandler(TokenService tokenService, ClientSyncService clientSyncService, JwtService jwtService, Domain domain) {
        this.tokenService = tokenService;
        this.clientSyncService = clientSyncService;
        this.jwtService = jwtService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {

        //Do not apply security check if open dynamic client registration is enabled.
        if(domain.isOpenDynamicClientRegistrationEnabled()) {
            LOGGER.debug("Open Dynamic client registration is enabled - no security will be performed.");
            context.put("domain",domain.getId());
            context.next();
            return;
        }

        //1st check if dynamic client registration is enabled.
        if(!domain.isDynamicClientRegistrationEnabled()) {
            LOGGER.debug("Dynamic client registration is disabled");
            context.fail(new ClientRegistrationForbiddenException());
            return;
        }

        //Else check if client is allowed thanks to the access_token authorization header.
        this.extractAccessTokenFromRequest(context)
                .flatMap(this::validateAccessToken)
                .subscribe(
                        token -> {
                            context.put(AccessToken.ACCESS_TOKEN, token);
                            context.put("domain",domain.getId());
                            context.next();
                        },
                        error -> context.fail(error)
                );
    }

    /**
     * @param accessToken String
     * @return AccessToken
     */
    private Maybe<Token> validateAccessToken(String accessToken) {
        return jwtService.decode(accessToken)
                .flatMapMaybe(jwt -> clientSyncService.findByClientId(jwt.getAud()))
                .switchIfEmpty(Maybe.error(new InvalidTokenException("Invalid or unknown client for this token")))
                .flatMap(client ->
                        this.tokenService.getAccessToken(accessToken,client)
                                .flatMap(token -> {
                                    if(!token.getSubject().equals(client.getClientId())) {
                                        //Token for application must contain clientId as subject
                                        return Maybe.error(new InvalidTokenException("The access token was not issued for a Client"));
                                    }
                                    if(token.getExpireAt().before(new Date())) {
                                        return Maybe.error(new InvalidTokenException("The access token expired"));
                                    }
                                    if (token.getScope() == null || !Arrays.asList(token.getScope().split("\\s+")).contains(Scope.DCR_ADMIN.getKey())) {
                                        return Maybe.error(new ClientRegistrationForbiddenException());
                                    }
                                    return Maybe.just(token);
                                })
                );
    }
}
