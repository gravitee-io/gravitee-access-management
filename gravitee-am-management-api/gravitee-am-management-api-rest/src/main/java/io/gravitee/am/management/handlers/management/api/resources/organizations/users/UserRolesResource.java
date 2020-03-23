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
package io.gravitee.am.management.handlers.management.api.resources.organizations.users;

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.Permission;
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
public class UserRolesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a user roles",
            notes = "User must have the ORGANIZATION_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User roles successfully fetched", response = Role.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.READ)
                .andThen(userService.findById(ReferenceType.ORGANIZATION, organizationId, user)
                        .flatMap(endUser -> {
                            if (endUser.getRoles() == null || endUser.getRoles().isEmpty()) {
                                return Single.just(Collections.emptyList());
                            }
                            return roleService.findByIdIn(endUser.getRoles());
                        }))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Assign roles to a user",
            notes = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Roles successfully assigned", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void assign(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @Valid @NotNull final List<String> roles,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(userService.assignRoles(ReferenceType.ORGANIZATION, organizationId, user, roles, authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @Path("{role}")
    public UserRoleResource getUserRoleResource() {
        return resourceContext.getResource(UserRoleResource.class);
    }

}
