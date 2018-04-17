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

import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedTokenType;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionRequest;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.token.TokenTypeHint;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * OAuth 2.0 Token Introspection Endpoint
 *
 * See <a href="https://tools.ietf.org/html/rfc7662"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IntrospectionEndpointHandler implements Handler<RoutingContext> {

    private final static String TOKEN_PARAM = "token";
    private final static String TOKEN_TYPE_HINT_PARAM = "token_type_hint";

    private IntrospectionService introspectionService;

    @Override
    public void handle(RoutingContext context) {
        introspectionService
                .introspect(createRequest(context))
                .doOnSuccess(introspectionResponse -> context.response()
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .end(Json.encodePrettily(introspectionResponse)))
                .subscribe();
    }

    private static IntrospectionRequest createRequest(RoutingContext context) {
        String token = context.request().getParam(TOKEN_PARAM);
        String tokenTypeHint = context.request().getParam(TOKEN_TYPE_HINT_PARAM);

        IntrospectionRequest introspectionRequest = new IntrospectionRequest(token);

        if (tokenTypeHint != null) {
            try {
                introspectionRequest.setHint(TokenTypeHint.from(tokenTypeHint));
            } catch (IllegalArgumentException iae) {
                throw new UnsupportedTokenType(tokenTypeHint);
            }
        }

        return introspectionRequest;
    }

    public void setIntrospectionService(IntrospectionService introspectionService) {
        this.introspectionService = introspectionService;
    }
}
