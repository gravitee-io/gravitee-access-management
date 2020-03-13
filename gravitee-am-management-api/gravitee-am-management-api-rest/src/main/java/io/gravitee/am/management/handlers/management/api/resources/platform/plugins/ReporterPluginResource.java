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

import io.gravitee.am.management.service.ReporterPluginService;
import io.gravitee.am.management.service.exception.ReporterPluginNotFoundException;
import io.gravitee.am.management.service.exception.ReporterPluginSchemaNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Reporter"})
public class ReporterPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private ReporterPluginService reporterPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a reporter plugin")
    public void get(
            @PathParam("reporter") String reporterId,
            @Suspended final AsyncResponse response) {
        reporterPluginService.findById(reporterId)
                .switchIfEmpty(Maybe.error(new ReporterPluginNotFoundException(reporterId)))
                .map(reporterPlugin -> Response.ok(reporterPlugin).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a reporter plugin's schema")
    public void getSchema(
            @PathParam("reporter") String reporterId,
            @Suspended final AsyncResponse response) {
        // Check that the identity provider exists
        reporterPluginService.findById(reporterId)
                .switchIfEmpty(Maybe.error(new ReporterPluginNotFoundException(reporterId)))
                .flatMap(irrelevant -> reporterPluginService.getSchema(reporterId))
                .switchIfEmpty(Maybe.error(new ReporterPluginSchemaNotFoundException(reporterId)))
                .map(reporterPluginSchema -> Response.ok(reporterPluginSchema).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error)
                );
    }
}
