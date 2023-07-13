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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.exception.EntrypointNotFoundException;
import io.gravitee.am.service.model.UpdateEntrypoint;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EntrypointResource extends AbstractResource {

    @Autowired
    private EntrypointService entrypointService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a sharding entrypoint",
            notes = "User must have the ORGANIZATION_ENTRYPOINT[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Sharding entrypoint", response = Entrypoint.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("entrypointId") String entrypointId, @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_ENTRYPOINT, Acl.READ)
                .andThen(entrypointService.findById(entrypointId, organizationId))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update the sharding entrypoint",
            notes = "User must have the ORGANIZATION_ENTRYPOINT[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Sharding entrypoint successfully updated", response = Entrypoint.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @ApiParam(name = "entrypoint", required = true) @Valid @NotNull final UpdateEntrypoint entrypointToUpdate,
            @PathParam("entrypointId") String entrypointId,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_ENTRYPOINT, Acl.UPDATE)
                .andThen(entrypointService.update(entrypointId, organizationId, entrypointToUpdate, authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete the sharding entrypoint",
            notes = "User must have the ORGANIZATION_ENTRYPOINT[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Sharding entrypoint successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("entrypointId") String entrypointId,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_ENTRYPOINT, Acl.DELETE)
                .andThen(entrypointService.delete(entrypointId, organizationId, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
