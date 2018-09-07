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

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.common.http.HttpHeaders;
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

    public UserInfoRequestParseHandler() {
    }

    public UserInfoRequestParseHandler(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void handle(RoutingContext context) {
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
                throw new InvalidRequestException("An access token is required");
            }

            int idx = authorization.indexOf(' ');
            if (idx <= 0 || !BEARER.equalsIgnoreCase(authorization.substring(0, idx))) {
                throw new InvalidRequestException("The access token must be sent using the Authorization header field");
            }

            accessToken = authorization.substring(idx + 1);
        }

        tokenService.getAccessToken(accessToken)
                .map(accessToken1 -> {
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
                .subscribe(
                        accessToken1 -> {
                            context.put(AccessToken.ACCESS_TOKEN, accessToken1);
                            context.next();
                        },
                        error -> context.fail(error),
                        () -> context.fail(new InvalidTokenException()));
    }
}
