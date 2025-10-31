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
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.model.NewAccountAccessToken;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import javax.inject.Named;
import java.net.URI;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class OrganizationUserResource extends AbstractResource {

    @Autowired
    @Named("managementOrganizationUserService")
    private OrganizationUserService organizationUserService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getOrganizationUser",
            summary = "Get a user",
            description = "User must have the ORGANIZATION_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserEntity.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
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
    @Operation(summary = "Update a user",
            operationId = "updateOrganizationUser",
            description = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void updateUser(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @Parameter(name = "user", required = true) @Valid @NotNull UpdateUser updateUser,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(organizationUserService.update(ReferenceType.ORGANIZATION, organizationId, user, updateUser, authenticatedUser)
                        .map(UserEntity::new))
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("/tokens")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getOrganizationUserTokens",
            summary = "Get tokens of a user",
            description = "User must have the ORGANIZATION_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User tokens successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountAccessToken.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getUserActiveTokens(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.READ)
                .andThen(organizationUserService.findAccountAccessTokens(organizationId, user).toList())
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("/tokens")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Generate an account access token for a user",
            operationId = "createAccountAccessToken",
            description = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponse(responseCode = "201", description = "Account access token generated",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AccountAccessToken.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void createAccountToken(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String userId,
            @Parameter(name = "name") NewAccountAccessToken newToken,
            @Suspended final AsyncResponse response
    ) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();
        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(organizationUserService.createAccountAccessToken(organizationId, userId, newToken, authenticatedUser))
                .map(accountToken -> Response
                        .created(URI.create("/organizations/" + organizationId + "/users/" + userId + "/tokens/" + accountToken.tokenId()))
                        .entity(accountToken)
                        .build())
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Path("/tokens/{tokenId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Revoke an account access token",
            operationId = "revokeAccountAccessToken",
            description = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponse(responseCode = "200", description = "Account access token revoked")
    public void revokeAccountToken(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String userId,
            @PathParam("tokenId") String tokenId,
            @Suspended final AsyncResponse response
    ) {
        final var authenticatedUser = getAuthenticatedUser();
        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(organizationUserService.revokeToken(organizationId, userId, tokenId, authenticatedUser))
                .map(revoked -> Response.noContent().build())
                .switchIfEmpty(Maybe.just(Response.status(Response.Status.NOT_FOUND).build()))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Path("/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a user status",
            operationId = "updateOrganizationUserStatus",
            description = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User status successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void updateUserStatus(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @Parameter(name = "status", required = true) @Valid @NotNull StatusEntity status,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(organizationUserService.updateStatus(organizationId, user, status.isEnabled(), authenticatedUser)
                    .map(UserEntity::new))
                .subscribe(response::resume, response::resume);
    }

    @PATCH
    @Path("/username")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateOrganisationUsername",
            summary = "Update a user username",
            description = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User username successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void updateUsername(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String userId,
            @Parameter(name = "username", required = true) @Valid @NotNull UsernameEntity username,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.UPDATE)
                .andThen(organizationUserService.updateUsername(ReferenceType.ORGANIZATION, organizationId, userId, username.getUsername().trim(), authenticatedUser))
                .map(UserEntity::new)
                .subscribe(response::resume, response::resume);
    }


    @DELETE
    @Operation(summary = "Delete a user",
            operationId = "deleteOrganizationUser",
            description = "User must have the ORGANIZATION_USER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.DELETE)
                .andThen(organizationUserService.delete(ReferenceType.ORGANIZATION, organizationId, user, authenticatedUser))
                .subscribe(u -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("resetPassword")
    @Operation(summary = "Reset password",
            operationId = "resetOrganizationUserPassword",
            description = "User must have the ORGANIZATION_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void resetPassword(
            @PathParam("organizationId") String organizationId,
            @PathParam("user") String user,
            @Parameter(name = "password", required = true) @Valid @NotNull PasswordValue password,
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
