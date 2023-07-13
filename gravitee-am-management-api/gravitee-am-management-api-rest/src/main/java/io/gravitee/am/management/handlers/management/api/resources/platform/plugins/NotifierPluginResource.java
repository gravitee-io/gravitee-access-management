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

import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import io.gravitee.am.management.service.impl.plugins.NotifierPluginService;
import io.gravitee.am.service.model.plugin.NotifierPlugin;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.inject.Inject;
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
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Notifier"})
public class NotifierPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private NotifierPluginService notifierPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a notifier",
            notes = "There is no particular permission needed. User must be authenticated.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Notifier plugin", response = NotifierPlugin.class),
            @ApiResponse(code = 404, message = "Notifier plugin not found", response = ErrorEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("notifierId") String notifierId,
            @Suspended final AsyncResponse response) {

        notifierPluginService.findById(notifierId)
                .map(notifierPlugin -> Response.ok(notifierPlugin).build())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a notifier plugin's schema",
            notes = "There is no particular permission needed. User must be authenticated.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Notifier plugin schema", response = String.class),
            @ApiResponse(code = 404, message = "Notifier plugin schema not found", response = ErrorEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void getSchema(
            @PathParam("notifierId") String notifierId,
            @Suspended final AsyncResponse response) {

        notifierPluginService.findById(notifierId)
                .flatMap(notifierPlugin -> notifierPluginService.getSchema(notifierPlugin.getId()))
                .map(notifierPluginSchema -> Response.ok(notifierPluginSchema).build())
                .onErrorResumeNext(err -> Single.just(Response.noContent().build()))
                .subscribe(response::resume, response::resume);
    }
}