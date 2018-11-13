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

import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.AccessToken;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.http.HttpServerRequest;
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
public class UserInfoRequestParseHandler implements Handler<RoutingContext> {

    private static final String BEARER = "Bearer";
    private static final String ACCESS_TOKEN_PARAM = "access_token";
    private static final String OPENID_SCOPE = "openid";
    private TokenService tokenService;
    private ClientService clientService;
    private JwtService jwtService;

    public UserInfoRequestParseHandler() {
    }

    public UserInfoRequestParseHandler(TokenService tokenService, ClientService clientService, JwtService jwtService) {
        this.tokenService = tokenService;
        this.clientService = clientService;
        this.jwtService = jwtService;
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

                context.put(Token.ACCESS_TOKEN, handler.result());
                context.next();
            });
        });
    }

    private void parseAccessToken(RoutingContext context, Handler<AsyncResult<String>> handler) {
        final HttpServerRequest request = context.request();
        String accessToken = null;

        // Try to get the access token from the body request
        if (request.method().equals(HttpMethod.POST)) {
            accessToken = context.request().getParam(ACCESS_TOKEN_PARAM);
        }

        // no access token try to get one from the HTTP Authorization header
        if (accessToken == null || accessToken.isEmpty()) {
            final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);

            if (authorization == null) {
                handler.handle(Future.failedFuture(new InvalidRequestException("An access token is required")));
                return;
            }

            int idx = authorization.indexOf(' ');
            if (idx <= 0 || !BEARER.equalsIgnoreCase(authorization.substring(0, idx))) {
                handler.handle(Future.failedFuture(new InvalidRequestException("The access token must be sent using the Authorization header field")));
                return;
            }
            accessToken = authorization.substring(idx + 1);
        }

        handler.handle(Future.succeededFuture(accessToken));
    }

    private void decodeAccessToken(String accessToken, Handler<AsyncResult<Token>> handler) {
        jwtService.decode(accessToken)
                .flatMapMaybe(jwt -> clientService.findByClientId(jwt.getAud()).switchIfEmpty(Maybe.error(new InvalidTokenException("Invalid or unknown client for this token"))))
                .flatMap(client -> tokenService.getAccessToken(accessToken, client)
                        .map(accessToken1 -> {
                            String subject = accessToken1.getSubject();
                            // The UserInfo Endpoint is an OAuth 2.0 Protected Resource that returns Claims about the authenticated End-User
                            if (subject.equals(client.getId())) {
                                throw new InvalidRequestException("The access token was not issued for an End-User");
                            }

                            if (accessToken1.getExpiresIn() == 0) {
                                throw new InvalidTokenException("The access token expired");
                            }
                            // The Access Token must be obtained from an OpenID Connect Authentication Request (i.e should have at least openid scope)
                            // https://openid.net/specs/openid-connect-core-1_0.html#UserInfoRequest
                            if (accessToken1.getScope() == null || !Arrays.asList(accessToken1.getScope().split("\\s+")).contains(OPENID_SCOPE)) {
                                throw new InvalidTokenException("Invalid access token scopes. The access token should have at least 'openid' scope");
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
