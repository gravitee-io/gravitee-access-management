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
import io.gravitee.am.gateway.handler.uma.resources.request.PermissionTicketRequest;
import io.gravitee.am.gateway.handler.uma.resources.response.PermissionTicketResponse;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.service.PermissionTicketService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <pre>
 * This endpoint is part of the UMA 2.0 Protection API.
 * The permission endpoint defines a means for the Resource Server to request one or more permissions (resource identifiers and corresponding scopes)
 * with the Authorization Server on the client's behalf, and to receive a permission ticket in return.
 * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#permission-endpoint">here</a>
 * </pre>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class PermissionEndpoint implements Handler<RoutingContext> {

    private PermissionTicketService permissionTicketService;
    private Domain domain;

    public PermissionEndpoint(Domain domain, PermissionTicketService permissionTicketService) {
        this.domain = domain;
        this.permissionTicketService = permissionTicketService;
    }

    @Override
    public void handle(RoutingContext context) {
        JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        this.extractRequest(context)
                .flatMap(this::bodyValidation)
                .map(this::toPermissionRequest)
                .flatMap(permissionRequests -> permissionTicketService.create(permissionRequests, domain.getId(), client.getId()))
                .map(PermissionTicketResponse::from)
                .subscribe(
                        permission -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.CREATED_201)
                                .end(Json.encodePrettily(permission))
                        , error -> context.fail(error)
                );
    }

    /**
     * Specification state :
     * Requesting multiple permissions might be appropriate, for example, in cases where the resource server expects the requesting party
     * to need access to several related resources if they need access to any one of the resources
     *
     * Means : Body can either be a JsonArray or a JsonObject, we need to handle both case.
     *
     * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#permission-endpoint">here</a>
     * @param context RoutingContext
     * @return List of PermissionRequest
     */
    private Single<List<PermissionTicketRequest>> extractRequest(RoutingContext context) {
        List<PermissionTicketRequest> result;
        Object json;

        try {
            json = context.getBody().toJson();
        }
        catch (RuntimeException err) {
            return Single.error(new InvalidRequestException("Unable to parse body permission request"));
        }

        if(json instanceof JsonArray) {
            result = convert(((JsonArray)json).getList());
        } else {
            result = Arrays.asList(((JsonObject)json).mapTo(PermissionTicketRequest.class));
        }
        return Single.just(result);
    }

    private List<PermissionTicketRequest> convert(List<LinkedHashMap> list) {
        return list.stream()
                .map(map -> new JsonObject(map).mapTo(PermissionTicketRequest.class))
                .collect(Collectors.toList());
    }

    private List<PermissionRequest> toPermissionRequest(List<PermissionTicketRequest> requestedPermissions) {
        return requestedPermissions.stream()
                .map(request -> new PermissionRequest().setResourceId(request.getResourceId()).setResourceScopes(request.getResourceScopes()))
                .collect(Collectors.toList());
    }

    /**
     * Both resource_id & resource_scopes are mandatory fields.
     */
    private Single<List<PermissionTicketRequest>> bodyValidation(List<PermissionTicketRequest> toValidate) {
        if(toValidate.stream().filter(invalidPermissionRequest()).count() > 0) {
            return Single.error(new InvalidRequestException("resource_id and resource_scopes are mandatory."));
        }
        return Single.just(toValidate);
    }

    /**
     * Rules inspired from specification <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#permission-endpoint">here</a>
     *
     * Requesting a permission with no scopes might be appropriate, for example, in cases where an access attempt involves an API call
     * that is ambiguous without further context (role-based scopes such as user and admin could have this ambiguous quality,
     * and an explicit client request for a particular scope at the token endpoint later can clarify the desired access).
     * @return
     */
    private static final Predicate<PermissionTicketRequest> invalidPermissionRequest() {
        return permission -> permission == null ||
                // Permission resource id is not correct
                (permission.getResourceId() == null || permission.getResourceId().isEmpty()) ||
                // Or Permission request contains empty string scopes (only checking for empty scope values, scopes may not be informed as defined into the specification)
                (permission.getResourceScopes() != null && !permission.getResourceScopes().isEmpty() && permission.getResourceScopes().stream().filter(s -> (s==null || s.isEmpty())).count()>0);
    }
}
