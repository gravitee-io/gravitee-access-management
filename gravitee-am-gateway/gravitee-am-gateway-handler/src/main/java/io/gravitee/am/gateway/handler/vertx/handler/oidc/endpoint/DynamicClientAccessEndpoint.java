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

import io.gravitee.am.common.oauth2.exception.OAuth2Exception;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oidc.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.gateway.handler.oidc.response.DynamicClientRegistrationResponse;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Client;
import io.gravitee.am.service.ClientService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This endpoint aim to access to client-id generated through the dynamic client registration protocol.
 * See <a href="https://openid.net/specs/openid-connect-registration-1_0.html">Openid Connect Dynamic Client Registration</a>
 * See <a href="https://tools.ietf.org/html/rfc7591"> OAuth 2.0 Dynamic Client Registration Protocol</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DynamicClientAccessEndpoint extends DynamicClientRegistrationEndpoint{

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicClientAccessEndpoint.class);

    public DynamicClientAccessEndpoint(DynamicClientRegistrationService dcrService, ClientService clientService, ClientSyncService clientSyncService) {
        super(dcrService, clientService, clientSyncService);
    }

    /**
     * Read client_metadata.
     * See <a href="https://openid.net/specs/openid-connect-registration-1_0.html#ReadRequest">Read Request</a>
     * See <a href="https://openid.net/specs/openid-connect-registration-1_0.html#ReadResponse">Read Response</a>
     *
     * @param context
     */
    public void read(RoutingContext context) {
        LOGGER.debug("Dynamic client registration GET endpoint");

        this.getClient(context)
                .map(DynamicClientRegistrationResponse::fromClient)
                .map(response -> {
                    //The Authorization Server need not include the registration access_token or client_uri unless they have been updated.
                    response.setRegistrationAccessToken(null);
                    response.setRegistrationClientUri(null);
                    return response;
                })
                .subscribe(
                        result -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(result))
                        , error -> context.fail(error)
                );
    }

    /**
     * Patch client_metadata.
     * @param context
     */
    public void patch(RoutingContext context) {
        LOGGER.debug("Dynamic client registration PATCH endpoint");

        this.getClient(context)
                .flatMapSingle(Single::just)
                .flatMap(client -> this.extractRequest(context)
                        .flatMap(dcrService::validateClientPatchRequest)
                        .map(request -> request.patch(client))
                        .flatMap(updatedClient -> dcrService.applyRegistrationAccessToken(UriBuilderRequest.extractBasePath(context), updatedClient))
                        .flatMap(clientService::update)
                        .map(clientSyncService::addDynamicClientRegistred)
                )
                .subscribe(
                        client -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(DynamicClientRegistrationResponse.fromClient(client)))
                        , error -> context.fail(error)
                );
    }

    /**
     * Update/Override client_metadata.
     * @param context
     */
    public void update(RoutingContext context) {
        LOGGER.debug("Dynamic client registration UPDATE endpoint");

        this.getClient(context)
                .flatMapSingle(Single::just)
                .flatMap(client -> this.extractRequest(context)
                        .flatMap(dcrService::validateClientRegistrationRequest)
                        .map(request -> request.update(client))
                        .flatMap(updatedClient -> dcrService.applyRegistrationAccessToken(UriBuilderRequest.extractBasePath(context), updatedClient))
                        .flatMap(clientService::update)
                        .map(clientSyncService::addDynamicClientRegistred)
                )
                .subscribe(
                        client -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(DynamicClientRegistrationResponse.fromClient(client)))
                        , error -> context.fail(error)
                );
    }

    /**
     * Delete client
     * @param context
     */
    public void delete(RoutingContext context) {
        LOGGER.debug("Dynamic client registration DELETE endpoint");

        this.getClient(context)
                .flatMapSingle(client -> this.clientService.delete(client.getId()).toSingleDefault(client))
                .map(this.clientSyncService::removeDynamicClientRegistred)
                .subscribe(
                        client -> context.response().setStatusCode(HttpStatusCode.NO_CONTENT_204).end()
                        , error -> context.fail(error)
                );
    }

    private Maybe<Client> getClient(RoutingContext context) {
        String domain = context.get("domain");
        String clientId = context.request().getParam("client_id");

        return this.clientSyncService.findByDomainAndClientId(domain,clientId)
                .map(Client::clone)
                .switchIfEmpty(Maybe.error(new OAuth2Exception() {
                    @Override
                    public int getHttpStatusCode() {
                        return HttpStatusCode.NOT_FOUND_404;
                    }

                    @Override
                    public String getMessage() {
                        return "client not found";
                    }
                }));
    }
}
