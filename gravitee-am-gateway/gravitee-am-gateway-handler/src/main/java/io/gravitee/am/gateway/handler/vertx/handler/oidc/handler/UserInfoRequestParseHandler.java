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
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.reactivex.Maybe;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Arrays;

/**
 * The Client sends the UserInfo Request using either HTTP GET or HTTP POST.
 * The Access Token obtained from an OpenID Connect Authentication Request MUST be sent as a Bearer Token, per Section 2 of OAuth 2.0 Bearer Token Usage [RFC6750].
 * It is RECOMMENDED that the request use the HTTP GET method and the Access Token be sent using the Authorization header field.
 *
 * See <a href="http://openid.net/specs/openid-connect-core-1_0.html#UserInfo">5.3.1. UserInfo Request</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserInfoRequestParseHandler extends AbstractProtectedHandler {

    private TokenService tokenService;
    private ClientSyncService clientSyncService;
    private JwtService jwtService;

    public UserInfoRequestParseHandler(TokenService tokenService, ClientSyncService clientSyncService, JwtService jwtService) {
        this.tokenService = tokenService;
        this.clientSyncService = clientSyncService;
        this.jwtService = jwtService;
    }

    @Override
    public void handle(RoutingContext context) {

        this.extractAccessTokenFromRequest(context)
                .flatMap(this::validateAccessToken)
                .subscribe(
                        token -> {
                            context.put(Token.ACCESS_TOKEN, token);
                            context.next();
                        },
                        throwable -> {
                            context.fail(throwable);
                            return;
                        },
                        () -> {
                            context.fail(new InvalidTokenException("The access token is invalid"));
                            return;
                        }
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
                                    if(token.getSubject().equals(client.getClientId())) {
                                        //Token for end user must not contain clientId as subject
                                        return Maybe.error(new InvalidRequestException("The access token was not issued for an End-User"));
                                    }
                                    if (token.getExpiresIn() == 0) {
                                        return Maybe.error(new InvalidTokenException("The access token expired"));
                                    }
                                    // The Access Token must be obtained from an OpenID Connect Authentication Request (i.e should have at least openid scope)
                                    // https://openid.net/specs/openid-connect-core-1_0.html#UserInfoRequest
                                    if (token.getScope() == null || !Arrays.asList(token.getScope().split("\\s+")).contains(Scope.OPENID.getKey())) {
                                        return Maybe.error(new InvalidTokenException("Invalid access token scopes. The access token should have at least 'openid' scope"));
                                    }
                                    return Maybe.just(token);
                                })
                );
    }
}
