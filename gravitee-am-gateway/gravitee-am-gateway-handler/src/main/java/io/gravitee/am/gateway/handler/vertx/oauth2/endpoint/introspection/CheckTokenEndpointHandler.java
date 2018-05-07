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

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.functions.Consumer;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CheckTokenEndpointHandler implements Handler<RoutingContext> {

    private final static String TOKEN_PARAM = "token";

    private TokenService tokenService;

    @Override
    public void handle(RoutingContext context) {
        String token = context.request().getParam(TOKEN_PARAM);

        tokenService.get(token)
                .doOnSuccess(new Consumer<AccessToken>() {
                    @Override
                    public void accept(AccessToken accessToken) throws Exception {
                        //TODO: check that the token has expired

                        context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(Json.encodePrettily(accessToken));
                    }
                })
                .doOnError(e -> {
                    // "Token was not recognised"
                    context.fail(new InvalidTokenException());

                    // Call global exception handler to display oauth2 exception error
                    // TODO trigger WARNING io.reactivex.exceptions.OnErrorNotImplementedException
                })
                .subscribe();
    }

    public void setTokenService(TokenService tokenService) {
        this.tokenService = tokenService;
    }
}
