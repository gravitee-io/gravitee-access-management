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
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.model.UpdateRole;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RoleResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private RoleService roleService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a platform role")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Role successfully fetched", response = RoleEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @io.gravitee.am.management.handlers.management.api.security.Permission(value = RolePermission.MANAGEMENT_ROLE, acls = RolePermissionAction.READ)
    })
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("role") String role,
            @Suspended final AsyncResponse response) {

        roleService.findById(ReferenceType.ORGANIZATION, organizationId, role)
                .map(this::convert)
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a platform role")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Role successfully updated", response = RoleEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @io.gravitee.am.management.handlers.management.api.security.Permission(value = RolePermission.MANAGEMENT_ROLE, acls = RolePermissionAction.UPDATE)
    })
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("role") String role,
            @ApiParam(name = "role", required = true) @Valid @NotNull UpdateRole updateRole,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        roleService.update(ReferenceType.ORGANIZATION, organizationId, role, updateRole, authenticatedUser)
                .map(this::convert)
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete a plaform role")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Role successfully deleted"),
            @ApiResponse(code = 400, message = "Role is bind to existing users"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @io.gravitee.am.management.handlers.management.api.security.Permission(value = RolePermission.MANAGEMENT_ROLE, acls = RolePermissionAction.DELETE)
    })
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("role") String role,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        roleService.delete(ReferenceType.ORGANIZATION, organizationId, role, authenticatedUser)
                .subscribe(() -> response.resume(Response.noContent().build()),
                        response::resume);
    }

    private RoleEntity convert(Role role) {
        RoleEntity roleEntity = new RoleEntity(role);
        if (role.getScope() != null) {
            try {
                Permission[] permissions = Permission.findByScope(RoleScope.valueOf(role.getScope()));
                List<String> availablePermissions = Arrays.asList(permissions).stream().map(Permission::getMask).sorted().collect(Collectors.toList());
                roleEntity.setAvailablePermissions(availablePermissions);
            } catch (Exception ex) {
            }
        }
        return roleEntity;
    }
}
