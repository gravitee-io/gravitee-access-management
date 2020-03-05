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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
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
    private DomainService domainService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a user roles")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User roles successfully fetched", response = Role.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_USER, acls = RolePermissionAction.READ)
    })
    public void list(@PathParam("domain") String domain,
                     @PathParam("user") String user,
                     @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(__ -> userService.findById(user))
                .switchIfEmpty(Maybe.error(new UserNotFoundException(user)))
                .flatMapSingle(endUser -> {
                    if (endUser.getRoles() == null || endUser.getRoles().isEmpty()) {
                        return Single.just(Collections.emptyList());
                    }
                    return roleService.findByIdIn(endUser.getRoles());
                })
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Assign roles to a user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Roles successfully assigned", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_USER, acls = RolePermissionAction.UPDATE)
    })
    public void assign(@PathParam("domain") String domain,
                       @PathParam("user") String user,
                       @Valid @NotNull final List<String> roles,
                       @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(endUser -> userService.assignRoles(ReferenceType.DOMAIN, domain, user, roles, authenticatedUser))
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{role}")
    public UserRoleResource getUserRoleResource() {
        return resourceContext.getResource(UserRoleResource.class);
    }

}
