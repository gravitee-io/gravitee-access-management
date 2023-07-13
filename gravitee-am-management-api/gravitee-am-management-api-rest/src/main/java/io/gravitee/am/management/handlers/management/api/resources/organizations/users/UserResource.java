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

import io.gravitee.am.management.handlers.management.api.model.PasswordValue;
import io.gravitee.am.management.handlers.management.api.model.StatusEntity;
import io.gravitee.am.management.handlers.management.api.model.UserEntity;
import io.gravitee.am.management.handlers.management.api.model.UsernameEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.management.service.impl.IdentityProviderManagerImpl;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.inject.Named;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class UserResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    @Named("managementOrganizationUserService")
    private OrganizationUserService organizationUserService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a user",
            notes = "User must have the ORGANIZATION_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User successfully fetched", response = UserEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.READ)
                .andThen(organizationUserService.findById(ReferenceType.ORGANIZATION, organizationId, user)
                        .map(UserEntity::new)
                        .flatMap(this::enhanceIdentityProvider))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a user",
            nickname = "updateOrganizationUser",
            notes = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateUser(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @ApiParam(name = "user", required = true) @Valid @NotNull UpdateUser updateUser,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(organizationUserService.update(ReferenceType.ORGANIZATION, organizationId, user, updateUser, authenticatedUser)
                    .map(UserEntity::new))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Path("/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a user status",
            nickname = "updateOrganizationUserStatus",
            notes = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User status successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateUserStatus(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @ApiParam(name = "status", required = true) @Valid @NotNull StatusEntity status,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(organizationUserService.updateStatus(ReferenceType.ORGANIZATION, organizationId, user, status.isEnabled(), authenticatedUser)
                    .map(UserEntity::new))
                .subscribe(response::resume, response::resume);
    }

    @PATCH
    @Path("/username")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "updateOrganisationUsername",
            value = "Update a user username",
            notes = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User username successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateUsername(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String userId,
            @ApiParam(name = "username", required = true) @Valid @NotNull UsernameEntity username,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(organizationUserService.updateUsername(ReferenceType.ORGANIZATION, organizationId, userId, username.getUsername(), authenticatedUser))
                .map(UserEntity::new)
                .subscribe(response::resume, response::resume);
    }


    @DELETE
    @ApiOperation(value = "Delete a user",
            nickname = "deleteOrganizationUser",
            notes = "User must have the ORGANIZATION_USER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "User successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.DELETE)
                .andThen(organizationUserService.delete(ReferenceType.ORGANIZATION, organizationId, user, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("resetPassword")
    @ApiOperation(value = "Reset password",
            nickname = "resetOrganizationUserPassword",
            notes = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Password reset"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void resetPassword(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @ApiParam(name = "password", required = true) @Valid @NotNull PasswordValue password,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(organizationUserService.findById(ReferenceType.ORGANIZATION, organizationId, user)
                        .filter(existingUser -> IdentityProviderManagerImpl.IDP_GRAVITEE.equals(existingUser.getSource()))
                        .switchIfEmpty(Maybe.error(new UserInvalidException("Unable to reset password")))
                        .flatMapCompletable(existingUser -> organizationUserService.resetPassword(organizationId, existingUser, password.getPassword(), authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);

    }
    private Single<UserEntity> enhanceIdentityProvider(UserEntity userEntity) {
        if (userEntity.getSource() != null) {
            return identityProviderService.findById(userEntity.getSource())
                    .flatMap(idP -> {
                        userEntity.setSource(idP.getName());
                        userEntity.setInternal(false);
                        // try to load the UserProvider to mark the user as internal or not
                        // Since Github issue #8695, the UserProvider maybe disabled for MongoDB & JDBC implementation
                        return identityProviderManager.getUserProvider(userEntity.getSourceId())
                                .map(up -> {
                                    userEntity.setInternal(true);
                                    return userEntity;
                                }).defaultIfEmpty(userEntity).toMaybe();
                    })
                    .defaultIfEmpty(userEntity);
        }
        return Single.just(userEntity);
    }
}
