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
package io.gravitee.am.gateway.handler.vertx.handler.scim.handler;

import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Arrays;

/**
 * The SCIM protocol is based upon HTTP and does not itself define a
 *    SCIM-specific scheme for authentication and authorization.  SCIM
 *    depends on the use of Transport Layer Security (TLS) and/or standard
 *    HTTP authentication and authorization schemes as per [RFC7235].
 *
 * Bearer Tokens
 *       Bearer tokens [RFC6750] MAY be used when combined with TLS and a
 *       token framework such as OAuth 2.0 [RFC6749].  Tokens that are
 *       issued based on weak or no authentication of authorizing users
 *       and/or OAuth clients SHOULD NOT be used, unless, for example, they
 *       are being used as single-use tokens to permit one-time requests
 *       such as anonymous registration (see Section 3.3).  For security
 *       considerations regarding the use of bearer tokens in SCIM, see
 *       Section 7.4.  While bearer tokens most often represent an
 *       authorization, it is assumed that the authorization was based upon
 *       a successful authentication of the SCIM client.  Accordingly, the
 *       SCIM service provider must have a method for validating, parsing,
 *       and/or "introspecting" the bearer token for the relevant
 *       authentication and authorization information.  The method for this
 *       is assumed to be defined by the token-issuing system and is beyond
 *       the scope of this specification.
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-2">2. Authentication and Authorization</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BearerTokensParseHandler implements Handler<RoutingContext> {

    private static final String BEARER = "Bearer";
    private static final String SCIM_SCOPE = "scim";
    private JwtService jwtService;
    private TokenService tokenService;
    private ClientSyncService clientService;

    public BearerTokensParseHandler(JwtService jwtService, TokenService tokenService, ClientSyncService clientService) {
        this.jwtService = jwtService;
        this.tokenService = tokenService;
        this.clientService = clientService;
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

                // token must have 'scim' scope
                Token token = handler.result();
                if (token.getScope() == null || token.getScope().isEmpty() || !Arrays.asList(token.getScope().split("\\s+")).contains(SCIM_SCOPE)) {
                    context.fail(new InvalidTokenException("Invalid access token scopes. The access token should have at least 'scim' scope"));
                }

                context.next();
            });
        });
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
