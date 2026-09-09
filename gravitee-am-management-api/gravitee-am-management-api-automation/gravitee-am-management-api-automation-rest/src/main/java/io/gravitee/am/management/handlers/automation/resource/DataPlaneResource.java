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

import io.gravitee.am.management.handlers.automation.mapper.AutomationDataPlaneMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationDataPlane;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.dataplane.ProvisionedDataPlaneLoader;
import io.reactivex.rxjava3.core.Completable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A single data plane, addressed by the id it was provisioned under.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Data Planes")
public class DataPlaneResource extends AbstractAutomationResource {

    @Autowired
    private DataPlaneDefinitionService dataPlaneDefinitionService;

    @Autowired
    private ProvisionedDataPlaneLoader provisionedDataPlaneLoader;

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationGetDataPlane", summary = "Get a data plane",
            description = "Retrieves a single Automation-managed data plane by its id.")
    @ApiResponse(responseCode = "200", description = "The data plane",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationDataPlane.class),
                    examples = @ExampleObject(name = "DataPlane", value = DataPlanesResource.READ_EXAMPLE)))
    @ApiResponse(responseCode = "404", description = "Data plane not found in this environment, or not managed " +
            "by the Automation API")
    public void get(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("dataPlaneId") String dataPlaneId,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DATA_PLANE, Acl.READ)
                .andThen(resolver.resolveDataPlane(environmentId, AutomationRef.parse(dataPlaneId)))
                .map(AutomationDataPlaneMapper::toAutomationDataPlane)
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(operationId = "automationDeleteDataPlane", summary = "Delete a data plane",
            description = "Deletes an Automation-managed data plane by its id. Deleting a data plane that " +
                    "does not exist also returns 204.")
    @ApiResponse(responseCode = "204", description = "Data plane successfully deleted")
    @ApiResponse(responseCode = "409", description = "At least one domain still references the data plane")
    public void delete(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("dataPlaneId") String dataPlaneId,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DATA_PLANE_MANAGED, Acl.DELETE)
                .andThen(resolver.resolveDataPlaneMaybe(environmentId, AutomationRef.parse(dataPlaneId)))
                .flatMapCompletable(dataPlane -> dataPlaneDefinitionService.delete(dataPlane.id(), principal)
                        .andThen(Completable.fromAction(() -> {
                            dataPlaneRegistry.unregister(dataPlane.id());
                            provisionedDataPlaneLoader.forget(dataPlane.id());
                        })))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
