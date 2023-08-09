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
package io.gravitee.am.management.handlers.management.api.resources.organizations.roles;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.RoleEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "role")})
public class RolesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private RoleService roleService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List registered roles of the organization",
            description = "User must have the ORGANIZATION_ROLE[LIST] permission on the specified organization. " +
                    "Each returned role is filtered and contains only basic information such as id, name, isSystem and assignableType.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List registered roles of the organization",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = RoleEntity.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @QueryParam("type") ReferenceType type,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_ROLE, Acl.LIST)
                .andThen(roleService.findAllAssignable(ReferenceType.ORGANIZATION, organizationId, type)
                        .map(this::filterRoleInfos)
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a role for the organization",
            description = "User must have the ORGANIZATION_ROLE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role successfully created"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @Parameter(name = "role", required = true) @Valid @NotNull final NewRole newRole,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_ROLE, Acl.CREATE)
                .andThen(roleService.create(ReferenceType.ORGANIZATION, organizationId, newRole, authenticatedUser)
                        .map(role -> Response
                                .created(URI.create("/organizations/" + organizationId + "/roles/" + role.getId()))
                                .entity(role)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{role}")
    public RoleResource getRoleResource() {
        return resourceContext.getResource(RoleResource.class);
    }

    private Role filterRoleInfos(Role role) {
        Role filteredRole = new Role();
        filteredRole.setId(role.getId());
        filteredRole.setName(role.getName());
        filteredRole.setDescription(role.getDescription());
        filteredRole.setSystem(role.isSystem());
        filteredRole.setDefaultRole(role.isDefaultRole());
        filteredRole.setAssignableType(role.getAssignableType());

        return filteredRole;
    }
}
