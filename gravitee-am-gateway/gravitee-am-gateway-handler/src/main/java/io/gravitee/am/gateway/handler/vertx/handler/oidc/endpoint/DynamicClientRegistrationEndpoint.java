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
package io.gravitee.am.gateway.handler.vertx.handler.oidc.endpoint;

import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oidc.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.gateway.handler.oidc.request.DynamicClientRegistrationRequest;
import io.gravitee.am.gateway.handler.oidc.response.DynamicClientRegistrationResponse;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

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

    protected ClientService clientService;
    protected ClientSyncService clientSyncService;
    protected DynamicClientRegistrationService dcrService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicClientRegistrationEndpoint.class);

    public DynamicClientRegistrationEndpoint(DynamicClientRegistrationService dcrService, ClientService clientService, ClientSyncService clientSyncService) {
        this.dcrService = dcrService;
        this.clientService = clientService;
        this.clientSyncService = clientSyncService;
    }

    /**
     * Main entrypoint, only used for client registration (creation)
     * @param context
     */
    @Override
    public void handle(RoutingContext context) {
        LOGGER.debug("Dynamic client registration CREATE endpoint");

        String domain = context.get("domain");

        this.extractRequest(context)
                .flatMap(dcrService::validateClientRegistrationRequest)
                .map(request -> dcrService.create(domain, request))
                .flatMap(dcrService::applyDefaultIdentityProvider)
                .flatMap(dcrService::applyDefaultCertificateProvider)
                .flatMap(client -> dcrService.applyRegistrationAccessToken(UriBuilderRequest.extractBasePath(context), client))
                .flatMap(clientService::create)
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
            return Single.error(ex);
        }
    }
}
