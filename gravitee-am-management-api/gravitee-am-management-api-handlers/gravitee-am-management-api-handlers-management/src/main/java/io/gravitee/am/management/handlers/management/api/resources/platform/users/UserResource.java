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
package io.gravitee.am.management.handlers.management.api.resources.platform.users;

import io.gravitee.am.management.handlers.management.api.model.ApplicationEntity;
import io.gravitee.am.management.handlers.management.api.model.PasswordValue;
import io.gravitee.am.management.handlers.management.api.model.StatusEntity;
import io.gravitee.am.management.handlers.management.api.model.UserEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Single;
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
public class UserResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private UserService userService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private PasswordValidator passwordValidator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User successfully fetched", response = UserEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_USER, acls = RolePermissionAction.READ)
    })
    public void get(@PathParam("user") String user,
                    @Suspended final AsyncResponse response) {

        String organizationId = "DEFAULT";

        userService.findById(ReferenceType.ORGANIZATION, organizationId, user)
                .map(UserEntity::new)
                .flatMap(this::enhanceIdentityProvider)
                .flatMap(this::enhanceClient)
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a user")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_USER, acls = RolePermissionAction.UPDATE)
    })
    public void updateUser(@PathParam("user") String user,
                           @ApiParam(name = "user", required = true) @Valid @NotNull UpdateUser updateUser,
                           @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        String organizationId = "DEFAULT";

        userService.update(ReferenceType.ORGANIZATION, organizationId, user, updateUser, authenticatedUser)
                .map(user1 -> Response.ok(user1).build())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Path("/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a user status")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User status successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_USER, acls = RolePermissionAction.UPDATE)
    })
    public void updateUserStatus(@PathParam("user") String user,
                                 @ApiParam(name = "status", required = true) @Valid @NotNull StatusEntity status,
                                 @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        String organizationId = "DEFAULT";

        userService.updateStatus(ReferenceType.ORGANIZATION, organizationId, user, status.isEnabled(), authenticatedUser)
                .map(user1 -> Response.ok(user1).build())
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete a user")
    @ApiResponses({
            @ApiResponse(code = 204, message = "User successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_USER, acls = RolePermissionAction.DELETE)
    })
    public void delete(@PathParam("user") String user,
                       @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        String organizationId = "DEFAULT";

        userService.delete(ReferenceType.ORGANIZATION, organizationId, user, authenticatedUser)
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("resetPassword")
    @ApiOperation(value = "Reset password")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Password reset"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_USER, acls = RolePermissionAction.UPDATE)
    })
    public void resetPassword(@PathParam("user") String user,
                              @ApiParam(name = "password", required = true) @Valid @NotNull PasswordValue password,
                              @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        String organizationId = "DEFAULT";

        // check password policy
        if (!passwordValidator.validate(password.getPassword())) {
            response.resume(new UserInvalidException(("Field [password] is invalid")));
            return;
        }

        userService.resetPassword(ReferenceType.ORGANIZATION, organizationId, user, password.getPassword(), authenticatedUser)
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);

    }

    @POST
    @Path("sendRegistrationConfirmation")
    @ApiOperation(value = "Send registration confirmation email")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Email sent"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_USER, acls = RolePermissionAction.UPDATE)
    })
    public void sendRegistrationConfirmation(@PathParam("user") String user,
                                             @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        String organizationId = "DEFAULT";

        userService.sendRegistrationConfirmation(ReferenceType.ORGANIZATION, organizationId, user, authenticatedUser)
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }


    @POST
    @Path("unlock")
    @ApiOperation(value = "Unlock a user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User unlocked"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_USER, acls = RolePermissionAction.UPDATE)
    })
    public void unlockUser(@PathParam("user") String user,
                           @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        String organizationId = "DEFAULT";

        userService.unlock(ReferenceType.ORGANIZATION, organizationId, user, authenticatedUser)
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);

    }

    @Path("roles")
    public UserRolesResource getUserRolesResource() {
        return resourceContext.getResource(UserRolesResource.class);
    }

    private Single<UserEntity> enhanceIdentityProvider(UserEntity userEntity) {
        if (userEntity.getSource() != null) {
            return identityProviderService.findById(userEntity.getSource())
                    .map(idP -> {
                        userEntity.setSource(idP.getName());
                        return userEntity;
                    })
                    .defaultIfEmpty(userEntity)
                    .toSingle();
        }
        return Single.just(userEntity);
    }

    private Single<UserEntity> enhanceClient(UserEntity userEntity) {
        if (userEntity.getClient() != null) {
            return applicationService.findById(userEntity.getClient())
                    .switchIfEmpty(Maybe.defer(() -> applicationService.findByDomainAndClientId(userEntity.getReferenceId(), userEntity.getClient())))
                    .map(application -> {
                        userEntity.setApplicationEntity(new ApplicationEntity(application));
                        return userEntity;
                    })
                    .defaultIfEmpty(userEntity)
                    .toSingle();
        }
        return Single.just(userEntity);
    }
}
