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
package io.gravitee.am.management.handlers.management.api.resources.platform.groups;

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.model.UpdateGroup;
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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private GroupService groupService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a platform group")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Group successfully fetched", response = Group.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_GROUP, acls = RolePermissionAction.READ)
    })
    public void get(@PathParam("group") String group,
                    @Suspended final AsyncResponse response) {

        String organizationId = "DEFAULT";

        groupService.findById(ReferenceType.ORGANIZATION, organizationId, group)
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a platform group")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Group successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_GROUP, acls = RolePermissionAction.UPDATE)
    })
    public void updateGroup(@PathParam("group") String group,
                            @ApiParam(name = "group", required = true) @Valid @NotNull UpdateGroup updateGroup,
                            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        String organizationId = "DEFAULT";

        groupService.update(ReferenceType.ORGANIZATION, organizationId, group, updateGroup, authenticatedUser)
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete a platform group")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Group successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_GROUP, acls = RolePermissionAction.DELETE)
    })
    public void delete(@PathParam("group") String group,
                       @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        String organizationId = "DEFAULT";

        groupService.delete(ReferenceType.ORGANIZATION, organizationId, group, authenticatedUser)
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @Path("members")
    public GroupMembersResource getGroupMembersResource() {
        return resourceContext.getResource(GroupMembersResource.class);
    }

    @Path("roles")
    public GroupRolesResource getGroupRolesResource() {
        return resourceContext.getResource(GroupRolesResource.class);
    }
}
