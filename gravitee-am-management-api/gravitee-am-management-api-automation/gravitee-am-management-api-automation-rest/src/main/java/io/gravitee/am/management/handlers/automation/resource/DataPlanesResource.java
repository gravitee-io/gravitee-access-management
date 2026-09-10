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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.automation.mapper.AutomationDataPlaneMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationDataPlane;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.dataplane.ProvisionedDataPlaneLoader;
import io.gravitee.am.service.exception.DataPlaneDefinitionNotFoundException;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

/**
 * Data planes managed under an environment.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Data Planes")
public class DataPlanesResource extends AbstractAutomationResource {

    static final String READ_EXAMPLE =
            "{\"id\":\"acme-eu\",\"name\":\"ACME EU data plane\",\"type\":\"mongodb\",\"gatewayUrl\":\"https://gateway-eu.example.com\","
                    + "\"database\":\"gravitee-am-acme\",\"hosts\":[\"mongo:27017\"],\"organizationId\":\"DEFAULT\","
                    + "\"environmentId\":\"DEFAULT\",\"createdAt\":\"2026-01-15T09:30:00.000Z\",\"updatedAt\":\"2026-01-15T09:30:00.000Z\"}";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DataPlaneDefinitionService dataPlaneDefinitionService;

    @Autowired
    private ProvisionedDataPlaneLoader provisionedDataPlaneLoader;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationListDataPlanes", summary = "List an environment's data planes",
            description = "Returns all data planes managed by the Automation API in the environment. Data " +
                    "planes provisioned outside the Automation API are not returned.")
    @ApiResponse(responseCode = "200", description = "List of data planes",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AutomationDataPlane.class)),
                    examples = @ExampleObject(name = "DataPlaneList", value = "[" + READ_EXAMPLE + "]")))
    public void list(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DATA_PLANE, Acl.LIST)
                .andThen(dataPlaneDefinitionService.findByEnvironmentId(environmentId)
                        .filter(dataPlane -> ManagedBy.AUTOMATION_API == dataPlane.managedBy())
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(nullToEmpty(o1.id()), nullToEmpty(o2.id())))
                        .map(AutomationDataPlaneMapper::toAutomationDataPlane)
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationCreateOrUpdateDataPlane",
            summary = "Create or update a data plane",
            description = "Idempotent create-or-update. Uses the id field in the body to identify the data " +
                    "plane within the environment. The type is immutable, and the organization and " +
                    "environment come from the path. An id: reference updates a data plane the Automation " +
                    "API does not manage, and cannot create one.")
    @ApiResponse(responseCode = "200", description = "The created or updated data plane",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationDataPlane.class),
                    examples = @ExampleObject(name = "DataPlane", value = READ_EXAMPLE)))
    @ApiResponse(responseCode = "400", description = "Invalid request: a missing required field, an unknown " +
            "data plane type, a configuration the type's plugin rejects, an id reserved by the node's " +
            "gravitee.yml, or an attempt to change an immutable field")
    @ApiResponse(responseCode = "404", description = "An id: reference naming a data plane that does not exist " +
            "in this environment")
    @ApiResponse(responseCode = "409", description = "The id is already taken by a data plane the Automation " +
            "API does not manage")
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Desired state of the data plane.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutomationDataPlane.class),
                            examples = {
                                    @ExampleObject(name = "DataPlane", description = "A MongoDB data plane",
                                            value = "{\"id\":\"acme-eu\",\"name\":\"ACME EU data plane\"," +
                                                    "\"type\":\"mongodb\",\"gatewayUrl\":\"https://gateway-eu.example.com\"," +
                                                    "\"configuration\":{\"mongodb\":{\"dbname\":\"gravitee-am-acme\"," +
                                                    "\"host\":\"mongo\",\"port\":27017}}}")
                            }))
            @Valid @NotNull AutomationDataPlane definition,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef ref = AutomationRef.parse(definition.getId());

        // The ACL is chosen from a non-erroring lookup so the permission gate runs before any
        // existence is revealed (404 for an unknown id: reference, 409 for a taken id).
        resolver.resolveDataPlaneMaybe(environmentId, ref)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(match -> {
                    Acl requiredAcl = match.isPresent() ? Acl.UPDATE : Acl.CREATE;
                    return checkAnyPermission(principal, organizationId, environmentId, Permission.DATA_PLANE, requiredAcl)
                            .andThen(Single.defer(() -> match
                                    .map(existing -> updateExisting(existing, definition, organizationId, environmentId, principal))
                                    .orElseGet(() -> createNew(ref, definition, organizationId, environmentId, principal))));
                })
                // this node registers the change itself; the others pick it up from the sync event
                .flatMap(dataPlane -> provisionedDataPlaneLoader.reload(dataPlane.getId()).toSingleDefault(dataPlane))
                .subscribe(response::resume, response::resume);
    }

    private Single<AutomationDataPlane> updateExisting(DataPlaneDefinitionSummary existing, AutomationDataPlane definition,
            String organizationId, String environmentId, User principal) {
        return dataPlaneDefinitionService.update(existing.id(),
                        AutomationDataPlaneMapper.toNewDataPlaneDefinition(definition, existing.id(), organizationId, environmentId),
                        principal)
                .map(AutomationDataPlaneMapper::toAutomationDataPlane);
    }

    private Single<AutomationDataPlane> createNew(AutomationRef ref, AutomationDataPlane definition,
            String organizationId, String environmentId, User principal) {
        if (ref.isId()) {
            return Single.error(new DataPlaneDefinitionNotFoundException(ref.raw()));
        }
        return dataPlaneDefinitionService.create(
                        AutomationDataPlaneMapper.toNewDataPlaneDefinition(definition, ref.raw(), organizationId, environmentId),
                        ManagedBy.AUTOMATION_API, principal)
                .map(AutomationDataPlaneMapper::toAutomationDataPlane);
    }

    @Path("/{dataPlaneId}")
    public DataPlaneResource getDataPlaneResource() {
        return resourceContext.getResource(DataPlaneResource.class);
    }
}
