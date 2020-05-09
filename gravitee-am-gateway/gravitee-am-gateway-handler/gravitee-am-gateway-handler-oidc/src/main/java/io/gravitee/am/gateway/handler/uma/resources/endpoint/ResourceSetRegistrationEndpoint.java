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
package io.gravitee.am.gateway.handler.uma.resources.endpoint;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.uma.resources.response.ResourceSetResponse;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.ResourceSet;
import io.gravitee.am.service.ResourceSetService;
import io.gravitee.am.service.exception.ResourceSetNotFoundException;
import io.gravitee.am.service.model.NewResourceSet;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;

import static io.gravitee.am.gateway.handler.uma.constants.UMAConstants.*;

/**
 * <pre>
 * This endpoint is part of the UMA 2.0 Protection API.
 * It enables the resource server (API) to put resources under the protection of the Authorization Server
 * on behalf of the resource owner, and manage them over time.
 * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#resource-registration-endpoint">here</a>
 * </pre>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class ResourceSetRegistrationEndpoint implements Handler<RoutingContext> {

    private ResourceSetService resourceSetService;
    private Domain domain;

    public ResourceSetRegistrationEndpoint(Domain domain, ResourceSetService resourceSetService) {
        this.domain = domain;
        this.resourceSetService = resourceSetService;
    }

    @Override
    public void handle(RoutingContext context) {
        JWT accessToken = context.get(OAuth2AuthHandler.TOKEN_CONTEXT_KEY);
        Client client = context.get(OAuth2AuthHandler.CLIENT_CONTEXT_KEY);

        this.resourceSetService.listByDomainAndClientAndUser(domain.getId(), client.getId(), accessToken.getSub())
                .flatMapPublisher(Flowable::fromIterable)
                .map(resourceSet -> resourceSet.getId())
                .collect(JsonArray::new, JsonArray::add)
                .subscribe(
                        buffer -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(buffer.isEmpty()?HttpStatusCode.NO_CONTENT_204:HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(buffer))
                        , error -> context.fail(error)
                );
    }

    public void create(RoutingContext context) {
        JWT accessToken = context.get(OAuth2AuthHandler.TOKEN_CONTEXT_KEY);
        Client client = context.get(OAuth2AuthHandler.CLIENT_CONTEXT_KEY);
        String basePath = UriBuilderRequest.extractBasePath(context);

        this.extractRequest(context)
                .flatMap(request -> this.resourceSetService.create(request, domain.getId(), client.getId(), accessToken.getSub()))
                .subscribe(
                        resourceSet -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .putHeader(HttpHeaders.LOCATION, resourceSetLocation(basePath, resourceSet))
                                .setStatusCode(HttpStatusCode.CREATED_201)
                                .end(Json.encodePrettily(ResourceSetResponse.from(resourceSet)))
                        , error -> context.fail(error)
                );
    }

    public void get(RoutingContext context) {
        JWT accessToken = context.get(OAuth2AuthHandler.TOKEN_CONTEXT_KEY);
        Client client = context.get(OAuth2AuthHandler.CLIENT_CONTEXT_KEY);
        String resource_id = context.request().getParam(RESOURCE_ID);

        this.resourceSetService.findByDomainAndClientAndUserAndResource(domain.getId(), client.getId(), accessToken.getSub(), resource_id)
                .switchIfEmpty(Single.error(new ResourceSetNotFoundException(resource_id)))
                .subscribe(
                        resourceSet -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(ResourceSetResponse.from(resourceSet)))
                        , error -> context.fail(error)
                );
    }

    /**
     * https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#reg-api
     * The spec state that if the resource can not be found, it must result in a 404.
     * By the way this may be better than a 403 to avoid confirming ids to a potential attacks.
     * @param context
     */
    public void update(RoutingContext context) {
        JWT accessToken = context.get(OAuth2AuthHandler.TOKEN_CONTEXT_KEY);
        Client client = context.get(OAuth2AuthHandler.CLIENT_CONTEXT_KEY);
        String resource_id = context.request().getParam(RESOURCE_ID);

        this.extractRequest(context)
                .flatMap(request -> this.resourceSetService.update(request, domain.getId(), client.getId(), accessToken.getSub(), resource_id))
                .subscribe(
                        resourceSet -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(ResourceSetResponse.from(resourceSet)))
                        , error -> context.fail(error)
                );
    }

    public void delete(RoutingContext context) {
        JWT accessToken = context.get(OAuth2AuthHandler.TOKEN_CONTEXT_KEY);
        Client client = context.get(OAuth2AuthHandler.CLIENT_CONTEXT_KEY);
        String resource_id = context.request().getParam(RESOURCE_ID);

        this.resourceSetService.delete(domain.getId(), client.getId(), accessToken.getSub(), resource_id)
                .subscribe(
                        () -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.NO_CONTENT_204)
                                .end()
                        , error -> context.fail(error)
                );
    }

    private Single<NewResourceSet> extractRequest(RoutingContext context) {
        return Single.just(context.getBodyAsJson())
                .flatMap(this::bodyValidation)
                .map(body -> body.mapTo(NewResourceSet.class));
    }

    private Single<JsonObject> bodyValidation(JsonObject body) {
        //Only one field is required from the spec, others are tag as optional
        if (body == null || !body.containsKey("resource_scopes")) {
            return Single.error(new InvalidRequestException("missing resource_scopes"));
        }
        return Single.just(body);
    }

    private String resourceSetLocation(String basePath, ResourceSet resourceSet) {
        return new StringBuilder()
                .append(basePath)
                .append(domain.getPath())
                .append(UMA_PATH)
                .append(RESOURCE_REGISTRATION_PATH)
                .append("/")
                .append(resourceSet.getId())
                .toString();
    }
}
