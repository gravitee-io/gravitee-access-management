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
package io.gravitee.am.gateway.handler.vertx.handler.users.handler;

import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.AccessToken;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Arrays;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthTokenParseHandler implements Handler<RoutingContext> {

    private static final String BEARER = "Bearer";
    private String requiredScope;
    private JwtService jwtService;
    private TokenService tokenService;
    private ClientSyncService clientService;

    public AuthTokenParseHandler(JwtService jwtService, TokenService tokenService, ClientSyncService clientService) {
        this.jwtService = jwtService;
        this.tokenService = tokenService;
        this.clientService = clientService;
    }

    public AuthTokenParseHandler(JwtService jwtService, TokenService tokenService, ClientSyncService clientService, String requiredScope) {
        this(jwtService, tokenService, clientService);
        this.requiredScope = requiredScope;
    }

    @Override
    public void handle(RoutingContext context) {
        parseAccessToken(context, parseHandler -> {
            if (parseHandler.failed()) {
                context.fail(parseHandler.cause());
                return;
            }

            decodeAccessToken(parseHandler.result(), handler -> {
                if (handler.failed()) {
                    context.fail(handler.cause());
                    return;
                }

                final String userId = context.request().getParam("userId");

                Token token = handler.result();
                context.put(AccessToken.ACCESS_TOKEN, token);

                // current user can show its account information
                if (userId != null && userId.equals(token.getSubject())) {
                    context.next();
                    return;
                }

                // token must have required scope
                if (token.getScope() == null || token.getScope().isEmpty() || !Arrays.asList(token.getScope().split("\\s+")).contains(requiredScope)) {
                    context.fail(new InvalidTokenException("Invalid access token scopes. The access token should have at least '"+ requiredScope +"' scope"));
                    return;
                }

                context.next();
            });
        });
    }

    public static AuthTokenParseHandler create(JwtService jwtService, TokenService tokenService, ClientSyncService clientService) {
        return new AuthTokenParseHandler(jwtService, tokenService, clientService);
    }

    public static AuthTokenParseHandler create(JwtService jwtService, TokenService tokenService, ClientSyncService clientService, String requiredScope) {
        return new AuthTokenParseHandler(jwtService, tokenService, clientService, requiredScope);
    }

    private void parseAccessToken(RoutingContext context, Handler<AsyncResult<String>> handler) {
        final HttpServerRequest request = context.request();
        final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);

        if (authorization == null) {
            handler.handle(Future.failedFuture(new InvalidTokenException("An access token is required")));
            return;
        }

        int idx = authorization.indexOf(' ');
        if (idx <= 0 || !BEARER.equalsIgnoreCase(authorization.substring(0, idx))) {
            handler.handle(Future.failedFuture(new InvalidTokenException("The access token must be sent using the Authorization header field")));
            return;
        }

        handler.handle(Future.succeededFuture(authorization.substring(idx + 1)));
    }

    private void decodeAccessToken(String accessToken, Handler<AsyncResult<Token>> handler) {
        jwtService.decode(accessToken)
                .flatMapMaybe(jwt -> clientService.findByClientId(jwt.getAud()).switchIfEmpty(Maybe.error(new InvalidTokenException("Invalid or unknown client for this token"))))
                .flatMap(client -> tokenService.getAccessToken(accessToken, client)
                        .map(accessToken1 -> {
                            if (accessToken1.getExpiresIn() == 0) {
                                throw new InvalidTokenException("The access token expired");
                            }
                            return accessToken1;
                        })
                )
                .subscribe(
                        accessToken1 -> handler.handle(Future.succeededFuture(accessToken1)),
                        error -> handler.handle(Future.failedFuture(error)),
                        () -> handler.handle(Future.failedFuture(new InvalidTokenException("The access token is invalid"))));
    }
}
