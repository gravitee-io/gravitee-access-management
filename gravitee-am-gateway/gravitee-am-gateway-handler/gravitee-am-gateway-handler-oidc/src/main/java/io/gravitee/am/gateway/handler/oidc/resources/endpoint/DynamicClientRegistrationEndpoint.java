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

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.converter.JWKSetDeserializer;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationRequest;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationResponse;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic Client Registration is a protocol that allows OAuth client applications to register with an OAuth server.
 * Specifications are defined by OpenID Foundation and by the IETF as RFC 7591 too.
 * They define how a client may submit a request to register itself and how should be the response.
 *
 * See <a href="https://openid.net/specs/openid-connect-registration-1_0.html">Openid Connect Dynamic Client Registration</a>
 * See <a href="https://tools.ietf.org/html/rfc7591"> OAuth 2.0 Dynamic Client Registration Protocol</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DynamicClientRegistrationEndpoint implements Handler<RoutingContext> {

    protected ClientSyncService clientSyncService;
    protected DynamicClientRegistrationService dcrService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicClientRegistrationEndpoint.class);

    public DynamicClientRegistrationEndpoint(DynamicClientRegistrationService dcrService, ClientSyncService clientSyncService) {
        this.dcrService = dcrService;
        this.clientSyncService = clientSyncService;
    }

    /**
     * Main entrypoint, only used for client registration (creation)
     * @param context
     */
    @Override
    public void handle(RoutingContext context) {
        LOGGER.debug("Dynamic client registration CREATE endpoint");

        this.extractRequest(context)
                .flatMap(request -> dcrService.create(request, UriBuilderRequest.resolveProxyRequest(context)))
                .map(clientSyncService::addDynamicClientRegistred)
                .subscribe(
                        client -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.CREATED_201)
                                .end(Json.encodePrettily(DynamicClientRegistrationResponse.fromClient(client)))
                        , error -> context.fail(error)
                );
    }

    protected Single<DynamicClientRegistrationRequest> extractRequest(RoutingContext context) {
        try{
            if(context.getBodyAsJson()==null) {
                throw new InvalidClientMetadataException("no content");
            }
            return Single.just(context.getBodyAsJson().mapTo(DynamicClientRegistrationRequest.class));
        }catch (Exception ex) {
            if(ex instanceof DecodeException) {
                return Single.error(new InvalidClientMetadataException(ex.getMessage()));
            }
            //Jackson mapper Replace Customs exception by an IllegalArgumentException
            if(ex instanceof IllegalArgumentException && ex.getMessage().startsWith(JWKSetDeserializer.PARSE_ERROR_MESSAGE)) {
                String sanitizedMessage = ex.getMessage().substring(0,ex.getMessage().indexOf(" (through reference chain:"));
                return Single.error(new InvalidClientMetadataException(sanitizedMessage));
            }
            return Single.error(ex);
        }
    }
}
