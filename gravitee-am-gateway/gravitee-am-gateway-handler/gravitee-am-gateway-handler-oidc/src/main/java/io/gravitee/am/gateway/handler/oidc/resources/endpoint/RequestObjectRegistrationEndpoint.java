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
package io.gravitee.am.gateway.handler.oidc.resources.endpoint;

import io.gravitee.am.common.exception.oauth2.MethodNotAllowedException;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectRegistrationRequest;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectRegistrationResponse;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.functions.Consumer;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See <a href="https://openid.net/specs/openid-financial-api-part-2.html#request-object-endpoint">7.  Request object endpoint</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RequestObjectRegistrationEndpoint implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestObjectRegistrationEndpoint.class);

    private static final String CLIENT_CONTEXT_KEY = "client";

    private RequestObjectService requestObjectService;

    public RequestObjectRegistrationEndpoint(RequestObjectService requestObjectService) {
        this.requestObjectService = requestObjectService;
    }

    @Override
    public void handle(RoutingContext context) {
        // Confidential clients or other clients issued client credentials MUST
        // authenticate with the authorization server when making requests to the request object registration endpoint.
        Client client = context.get(CLIENT_CONTEXT_KEY);
        if (client == null) {
            throw new InvalidClientException();
        }

        RequestObjectRegistrationRequest request = new RequestObjectRegistrationRequest();
        request.setRequest(context.getBodyAsString());
        request.setOrigin(extractOrigin(context.request()));

        requestObjectService.registerRequestObject(request, client)
                .subscribe(new Consumer<RequestObjectRegistrationResponse>() {
                    @Override
                    public void accept(RequestObjectRegistrationResponse response) throws Exception {
                        context.response()
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .end(Json.encodePrettily(response));
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        context.fail(throwable);
                    }
                });
    }

    private String extractOrigin(HttpServerRequest request) {
        String basePath = "/";
        try {
            basePath = UriBuilderRequest.resolveProxyRequest(request, "/", null);
        } catch (Exception e) {
            LOGGER.error("Unable to resolve OAuth 2.0 Authorization Request origin uri", e);
        }
        return basePath;
    }

    public static class MethodNotAllowedHandler implements Handler<RoutingContext> {

        @Override
        public void handle(RoutingContext context) {
            context.fail(new MethodNotAllowedException());
        }
    }
}
