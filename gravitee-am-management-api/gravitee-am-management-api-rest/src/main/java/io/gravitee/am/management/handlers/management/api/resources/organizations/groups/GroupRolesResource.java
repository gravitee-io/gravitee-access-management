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
package io.gravitee.am.management.handlers.management.api.resources.organizations.groups;

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.RoleService;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.swagger.annotations.ApiOperation;
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
import java.util.Collections;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupRolesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RoleService roleService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a group roles")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Group roles successfully fetched", response = Role.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_GROUP, acls = RolePermissionAction.READ)
    })
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("group") String group,
            @Suspended final AsyncResponse response) {

        groupService.findById(ReferenceType.ORGANIZATION, organizationId, group)
                .flatMap(group1 -> {
                    if (group1.getRoles() == null || group1.getRoles().isEmpty()) {
                        return Single.just(Collections.emptyList());
                    }
                    return roleService.findByIdIn(group1.getRoles());
                })
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Assign roles to a group")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Roles successfully assigned", response = Group.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_GROUP, acls = RolePermissionAction.UPDATE)
    })
    public void assign(
            @PathParam("organizationId") String organizationId,
            @PathParam("group") String group,
            @Valid @NotNull final List<String> roles,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        groupService.assignRoles(ReferenceType.ORGANIZATION, organizationId, group, roles, authenticatedUser)
                .subscribe(response::resume, response::resume);
    }

    @Path("{role}")
    public GroupRoleResource getGroupRoleResource() {
        return resourceContext.getResource(GroupRoleResource.class);
    }
}