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
package io.gravitee.am.management.handlers.management.api.resources.platform.roles;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.RoleEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"role"})
public class RolesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private RoleService roleService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered roles of the platform")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered roles of the platform", response = RoleEntity.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@QueryParam("scope") RoleScope scope,
                     @Suspended final AsyncResponse response) {

        String organizationId = "DEFAULT";

        roleService.findAll(ReferenceType.ORGANIZATION, organizationId)
                .map(roles -> {
                    List<RoleEntity> sortedRoles = roles.stream()
                            // filter by scope
                            .filter(role -> {
                                if (scope == null) {
                                    return true;
                                }
                                return role.getScope() != null && scope.getId() == role.getScope();
                            })
                            // if scope is not management return only non system role
                            .filter(role -> {
                                if (scope == null || RoleScope.MANAGEMENT.getId() == role.getScope()) {
                                    return true;
                                }
                                return !role.isSystem();
                            })
                            .map(RoleEntity::new)
                            .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                            .collect(Collectors.toList());
                    return Response.ok(sortedRoles).build();
                })
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a platform role")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Role successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_ROLE, acls = RolePermissionAction.CREATE)
    })
    public void create(@ApiParam(name = "role", required = true) @Valid @NotNull final NewRole newRole,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        String organizationId = "DEFAULT";

        roleService.create(ReferenceType.ORGANIZATION, organizationId, newRole, authenticatedUser)
                .map(role -> Response
                        .created(URI.create("/platform/roles/" + role.getId()))
                        .entity(role)
                        .build())
                .subscribe(response::resume, response::resume);
    }

    @Path("{role}")
    public RoleResource getRoleResource() {
        return resourceContext.getResource(RoleResource.class);
    }
}
