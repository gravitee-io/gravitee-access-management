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
package io.gravitee.am.management.handlers.management.api.resources.platform.plugins;

import io.gravitee.am.management.service.AuthorizationEnginePluginService;
import io.gravitee.am.management.service.exception.AuthorizationEnginePluginNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "Plugin"), @Tag(name= "Authorization Engine")})
public class AuthorizationEnginePluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private AuthorizationEnginePluginService authorizationEnginePluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getAuthorizationEnginePlugin",
            summary = "Get an authorization engine plugin",
            description = "There is no particular permission needed. User must be authenticated.")
    public void get(@PathParam("authorizationEngine") String authorizationEngineId,
                    @Suspended final AsyncResponse response) {
        authorizationEnginePluginService.findById(authorizationEngineId)
                .switchIfEmpty(Maybe.error(new AuthorizationEnginePluginNotFoundException(authorizationEngineId)))
                .map(plugin -> Response.ok(plugin).build())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getAuthorizationEnginePluginSchema",
            summary = "Get an authorization engine plugin's schema",
            description = "There is no particular permission needed. User must be authenticated.")
    public void getSchema(@PathParam("authorizationEngine") String authorizationEngineId,
                          @Suspended final AsyncResponse response) {
        authorizationEnginePluginService.getSchema(authorizationEngineId)
                .map(schema -> Response.ok(schema).build())
                .switchIfEmpty(Maybe.just(Response.noContent().build()))
                .subscribe(response::resume, response::resume);
    }
}
