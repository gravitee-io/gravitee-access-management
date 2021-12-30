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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.par;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.MethodNotAllowedException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestService;
import io.gravitee.am.identityprovider.common.oauth2.utils.URLEncodedUtils;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.PushedAuthorizationRequest;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PushedAuthorizationRequestEndpoint implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushedAuthorizationRequestEndpoint.class);

    private final PushedAuthorizationRequestService parService;

    public PushedAuthorizationRequestEndpoint(PushedAuthorizationRequestService parService) {
        this.parService = parService;
    }

    @Override
    public void handle(RoutingContext context) {
        // Confidential clients or other clients issued client credentials MUST
        // authenticate with the authorization server when making requests to the pushed authorization request endpoint.
        Client client = context.get(CLIENT_CONTEXT_KEY);
        if (client == null) {
            throw new InvalidClientException();
        }

        final String contentType = context.request().getHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !contentType.startsWith(URLEncodedUtils.CONTENT_TYPE)) {
            throw new InvalidRequestException("Unsupported Content-Type");
        }


        PushedAuthorizationRequest request = new PushedAuthorizationRequest();
        request.setParameters(extractRequestParameters(context.request()));
        request.setClient(client.getClientId());

        parService.registerParameters(request, client)
                .subscribe(
                        response -> {
                            context.response()
                                    .setStatusCode(HttpStatusCode.CREATED_201)
                                    .putHeader(io.gravitee.common.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                    .putHeader(io.gravitee.common.http.HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(io.gravitee.common.http.HttpHeaders.PRAGMA, "no-cache")
                                    .end(Json.encodePrettily(response));
                        },
                        throwable -> {
                            context.fail(throwable);
                        }
                );
    }

    private MultiValueMap<String, String> extractRequestParameters(HttpServerRequest request) {
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>(request.params().size());
        request.params().entries().forEach(entry -> requestParameters.add(entry.getKey(), entry.getValue()));
        return requestParameters;
    }

    public static class MethodNotAllowedHandler implements Handler<RoutingContext> {
        @Override
        public void handle(RoutingContext context) {
            context.fail(new MethodNotAllowedException());
        }
    }
}
