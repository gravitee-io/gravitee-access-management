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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.model.Environment;
import io.gravitee.am.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Tag(name = "Environments")
public class EnvironmentResource extends AbstractAutomationResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private EnvironmentService environmentService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationGetEnvironment", summary = "Get an environment")
    @ApiResponse(responseCode = "200", description = "The environment",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Environment.class)))
    public void get(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @Suspended final AsyncResponse response) {

        environmentService.findById(environmentId, organizationId)
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(operationId = "automationDeleteEnvironment", summary = "Delete an environment")
    @ApiResponse(responseCode = "204", description = "Environment deleted")
    public void delete(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @Suspended final AsyncResponse response) {
        response.resume(Response.status(Response.Status.METHOD_NOT_ALLOWED)
                .entity("Environment deletion is not supported via the Automation API")
                .build());
    }

    @jakarta.ws.rs.Path("/domains")
    public DomainsResource getDomainsResource() {
        return resourceContext.getResource(DomainsResource.class);
    }
}
