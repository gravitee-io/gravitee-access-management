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

import io.gravitee.am.management.service.ResourcePluginService;
import io.gravitee.am.management.service.exception.ResourcePluginNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

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
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Resource"})
public class ResourcePluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private ResourcePluginService resourcePluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a resource plugin",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void get(@PathParam("resource") String resourceId,
                    @Suspended final AsyncResponse response) {

        resourcePluginService.findById(resourceId)
                .switchIfEmpty(Maybe.error(new ResourcePluginNotFoundException(resourceId)))
                .map(resourcePlugin -> Response.ok(resourcePlugin).build())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a resource plugin's schema",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void getSchema(@PathParam("resource") String resourceId,
                          @Suspended final AsyncResponse response) {

        resourcePluginService.findById(resourceId)
                .flatMap(irrelevant -> resourcePluginService.getSchema(resourceId))
                .map(policyPluginSchema -> Response.ok(policyPluginSchema).build())
                .switchIfEmpty(Maybe.just(Response.noContent().build()))
                .subscribe(response::resume, response::resume);
    }
}