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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.service.uma.UMAResourceGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.uma.resources.response.ResourceResponse;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.service.exception.ResourceNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.NewResource;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.gateway.handler.uma.constants.UMAConstants.RESOURCE_ID;
import static io.gravitee.am.gateway.handler.uma.constants.UMAConstants.RESOURCE_REGISTRATION_PATH;
import static io.gravitee.am.gateway.handler.uma.constants.UMAConstants.UMA_PATH;

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
public class ResourceRegistrationEndpoint implements Handler<RoutingContext> {

    private final UMAResourceGatewayService resourceService;
    private final Domain domain;
    private final SubjectManager subjectManager;

    public ResourceRegistrationEndpoint(Domain domain, UMAResourceGatewayService resourceService, SubjectManager subjectManager) {
        this.domain = domain;
        this.resourceService = resourceService;
        this.subjectManager = subjectManager;
    }

    @Override
    public void handle(RoutingContext context) {
        JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        subjectManager.findUserIdBySub(accessToken)
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(accessToken.getSub())))
                .flatMap(userId -> this.resourceService.listByClientAndUser(client.getId(), userId.id())
                        .map(Resource::getId)
                        .collect(JsonArray::new, JsonArray::add))
                .subscribe(
                        buffer -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(buffer.isEmpty() ? HttpStatusCode.NO_CONTENT_204 : HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(buffer))
                        , error -> context.fail(error)
                );
    }

    public void create(RoutingContext context) {
        JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        String basePath = UriBuilderRequest.resolveProxyRequest(context);

        this.extractRequest(context)
                .flatMap(request -> subjectManager.findUserIdBySub(accessToken)
                        .switchIfEmpty(Single.error(() -> new UserNotFoundException(accessToken.getSub())))
                        .flatMap(userId -> this.resourceService.create(request, client.getId(), userId.id()/*todo degraded mode: should we use full id here as well?*/)))
                .subscribe(
                        resource -> {
                            final String resourceLocation = resourceLocation(basePath, resource);
                            context.response()
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                    .putHeader(HttpHeaders.LOCATION, resourceLocation)
                                    .setStatusCode(HttpStatusCode.CREATED_201)
                                    .end(Json.encodePrettily(ResourceResponse.from(resource, resourceLocation)));
                        }
                        , error -> context.fail(error)
                );
    }

    public void get(RoutingContext context) {
        JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        String resource_id = context.request().getParam(RESOURCE_ID);

        subjectManager.findUserIdBySub(accessToken)
                .switchIfEmpty(Maybe.error(() -> new UserNotFoundException(accessToken.getSub())))
                .flatMap(useId -> this.resourceService.findByClientAndUserAndResource(client.getId(), useId.id()/*todo degraded mode: should we use full id here as well?*/, resource_id))
                .switchIfEmpty(Single.error(new ResourceNotFoundException(resource_id)))
                .subscribe(
                        resource -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(ResourceResponse.from(resource)))
                        , error -> context.fail(error)
                );
    }

    /**
     * https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#reg-api
     * The spec state that if the resource can not be found, it must result in a 404.
     * By the way this may be better than a 403 to avoid confirming ids to a potential attacks.
     *
     * @param context
     */
    public void update(RoutingContext context) {
        JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        String resource_id = context.request().getParam(RESOURCE_ID);

        this.extractRequest(context)
                .flatMap(request -> subjectManager.findUserIdBySub(accessToken)
                        .switchIfEmpty(Single.error(() -> new UserNotFoundException(accessToken.getSub())))
                        .flatMap(userId -> this.resourceService.update(request, client.getId(), userId.id()/*todo degraded mode: should we use full id here as well?*/, resource_id)))
                .subscribe(
                        resource -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(ResourceResponse.from(resource)))
                        , error -> context.fail(error)
                );
    }

    public void delete(RoutingContext context) {
        JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        String resource_id = context.request().getParam(RESOURCE_ID);

        subjectManager.findUserIdBySub(accessToken)
                .flatMapCompletable(userId -> this.resourceService.delete(client.getId(), userId.id()/*todo degraded mode: should we use full id here as well?*/, resource_id))
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

    private Single<NewResource> extractRequest(RoutingContext context) {
        return Single.just(context.getBodyAsJson())
                .flatMap(this::bodyValidation)
                .map(body -> body.mapTo(NewResource.class));
    }

    private Single<JsonObject> bodyValidation(JsonObject body) {
        //Only one field is required from the spec, others are tag as optional
        if (body == null || !body.containsKey("resource_scopes")) {
            return Single.error(new InvalidRequestException("missing resource_scopes"));
        }
        return Single.just(body);
    }

    private String resourceLocation(String basePath, Resource resource) {
        return basePath +
                UMA_PATH +
                RESOURCE_REGISTRATION_PATH +
                "/" +
                resource.getId();
    }
}
