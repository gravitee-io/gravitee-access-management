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
package io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.introspection;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @deprecated Fall back to Gravitee.AM earlier versions
 *
 * OAuth 2.0 Token Introspection Endpoint but the response does not follow the rfc
 * Responds 200 OK with access token if access token is valid or 401 invalid token if token is invalid or expired
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CheckTokenEndpointHandler implements Handler<RoutingContext> {

    private final static String TOKEN_PARAM = "token";

    private TokenService tokenService;

    @Override
    public void handle(RoutingContext context) {
        // If the protected resource uses OAuth 2.0 client credentials to
        // authenticate to the introspection endpoint and its credentials are
        // invalid, the authorization server responds with an HTTP 401
        User authenticatedUser = context.user();
        if (authenticatedUser == null || ! (authenticatedUser.getDelegate() instanceof Client)) {
            throw new InvalidClientException();
        }

        String token = context.request().getParam(TOKEN_PARAM);
        if (token == null) {
            throw new InvalidRequestException();
        }

        tokenService.get(token)
                .map(accessToken -> {
                    if (accessToken.getExpiresIn() == 0) {
                        throw new InvalidTokenException("Token is expired");
                    }
                    return accessToken;
                })
                .subscribe(
                        accessToken -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(Json.encodePrettily(accessToken)),
                        error -> context.fail(new InvalidTokenException()),
                        () -> context.fail(new InvalidTokenException("Token was not recognised")));
    }

    public void setTokenService(TokenService tokenService) {
        this.tokenService = tokenService;
    }
}
