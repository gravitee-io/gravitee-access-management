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
package io.gravitee.am.management.handlers.management.api.resources.organizations.entrypoints;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.model.NewEntrypoint;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "entrypoints")
public class EntrypointsResource extends AbstractResource {

    @Autowired
    private EntrypointService entrypointService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listEntrypoints",
            summary = "List entrypoints",
            description = "User must have the ORGANIZATION[LIST] permission on the specified organization. " +
                    "Each returned entrypoint is filtered and contains only basic information such as id and name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List all the entrypoints",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = Entrypoint.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_ENTRYPOINT, Acl.LIST)
                .andThen(entrypointService.findAll(organizationId))
                .map(this::filterEntrypointInfos)
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                .toList()
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createEntrypoint",
            summary = "Create a entrypoint",
            description = "User must have the ORGANIZATION_ENTRYPOINT[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entrypoint successfully created"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @Parameter(name = "entrypoint", required = true)
            @Valid @NotNull final NewEntrypoint newEntrypoint,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_ENTRYPOINT, Acl.CREATE)
                .andThen(entrypointService.create(organizationId, newEntrypoint, authenticatedUser))
                .subscribe(entrypoint -> response.resume(Response
                                .created(URI.create("/organizations/" + organizationId + "/entrypoints/" + entrypoint.getId()))
                                .entity(entrypoint)
                                .build()),
                        response::resume);
    }

    @Path("{entrypointId}")
    @Operation(summary = "Get an entrypoint by its identifier", operationId = "getEntrypoint")
    public EntrypointResource getEntrypointResource() {
        return resourceContext.getResource(EntrypointResource.class);
    }

    private Entrypoint filterEntrypointInfos(Entrypoint entrypoint) {
        Entrypoint filteredEntrypoint = new Entrypoint();
        filteredEntrypoint.setId(entrypoint.getId());
        filteredEntrypoint.setName(entrypoint.getName());
        filteredEntrypoint.setUrl(entrypoint.getUrl());
        filteredEntrypoint.setDescription(entrypoint.getDescription());
        filteredEntrypoint.setDefaultEntrypoint(entrypoint.isDefaultEntrypoint());

        return filteredEntrypoint;
    }
}
