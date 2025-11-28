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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionRequest;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;

/**
 * OAuth 2.0 Token Introspection Endpoint
 *
 * See <a href="https://tools.ietf.org/html/rfc7662"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IntrospectionEndpoint implements Handler<RoutingContext> {
    private IntrospectionService introspectionService;

    public IntrospectionEndpoint(IntrospectionService introspectionService) {
        this.introspectionService = introspectionService;
    }

    @Override
    public void handle(RoutingContext context) {
        try {
            IntrospectionRequest request = createRequest(context);
            introspectionService
                .introspect(request)
                .doOnSuccess(introspectionResponse -> context.response()
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .end(Json.encodePrettily(introspectionResponse)))
                .subscribe();
        } catch (InvalidClientException | InvalidRequestException e) {
            context.fail(e);
        }
    }

    private IntrospectionRequest createRequest(RoutingContext context) {
        // If the protected resource uses OAuth 2.0 client credentials to
        // authenticate to the introspection endpoint and its credentials are
        // invalid, the authorization server responds with an HTTP 401
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (client == null) {
            throw new InvalidClientException();
        }

        String token = context.request().getParam(ConstantKeys.TOKEN_PARAM_KEY);
        if (token == null) {
            throw new InvalidRequestException();
        }

        String tokenTypeHint = context.request().getParam(ConstantKeys.TOKEN_TYPE_HINT_PARAM_KEY);

        return IntrospectionRequest.builder()
            .token(token)
            .tokenTypeHint(tokenTypeHint)
            .callerClientId(client.getClientId())
            .build();
    }
}
