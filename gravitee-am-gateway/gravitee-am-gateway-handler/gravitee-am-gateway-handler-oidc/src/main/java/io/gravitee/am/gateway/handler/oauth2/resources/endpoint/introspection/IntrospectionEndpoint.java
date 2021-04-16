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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.introspection;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedTokenType;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionRequest;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionService;
import io.gravitee.am.model.oidc.Client;
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
public class IntrospectionEndpoint implements Handler<RoutingContext> {

    private static final String CONTEXT_CLIENT_KEY = "client";
    private static final String TOKEN_PARAM = "token";
    private static final String TOKEN_TYPE_HINT_PARAM = "token_type_hint";

    private IntrospectionService introspectionService;

    public IntrospectionEndpoint() {}

    public IntrospectionEndpoint(IntrospectionService introspectionService) {
        this.introspectionService = introspectionService;
    }

    @Override
    public void handle(RoutingContext context) {
        // If the protected resource uses OAuth 2.0 client credentials to
        // authenticate to the introspection endpoint and its credentials are
        // invalid, the authorization server responds with an HTTP 401
        Client client = context.get(CONTEXT_CLIENT_KEY);
        if (client == null) {
            throw new InvalidClientException();
        }

        introspectionService
            .introspect(createRequest(context))
            .doOnSuccess(
                introspectionResponse ->
                    context
                        .response()
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .end(Json.encodePrettily(introspectionResponse))
            )
            .subscribe();
    }

    private static IntrospectionRequest createRequest(RoutingContext context) {
        String token = context.request().getParam(TOKEN_PARAM);
        String tokenTypeHint = context.request().getParam(TOKEN_TYPE_HINT_PARAM);

        if (token == null) {
            throw new InvalidRequestException();
        }

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
