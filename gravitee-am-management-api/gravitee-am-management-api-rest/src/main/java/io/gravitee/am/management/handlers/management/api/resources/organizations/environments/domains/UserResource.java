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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.management.handlers.management.api.model.ApplicationEntity;
import io.gravitee.am.management.handlers.management.api.model.PasswordValue;
import io.gravitee.am.management.handlers.management.api.model.StatusEntity;
import io.gravitee.am.management.handlers.management.api.model.UserEntity;
import io.gravitee.am.management.handlers.management.api.model.UsernameEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.management.service.ManagementUserService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
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
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class UserResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ManagementUserService userService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderServiceProxy identityProviderService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findUser",
            summary = "Get a user",
            description = "User must have the DOMAIN_USER[READ] permission on the specified domain " +
                    "or DOMAIN_USER[READ] permission on the specified environment " +
                    "or DOMAIN_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserEntity.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(domain -> userService.findById(domain, user))
                        .switchIfEmpty(Maybe.error(new UserNotFoundException(user)))
                        .flatMap(user1 -> {
                            if (user1.getReferenceType() == ReferenceType.DOMAIN
                                    && !user1.getReferenceId().equalsIgnoreCase(domainId)) {
                                return Maybe.error(new BadRequestException("User does not belong to domain"));
                            }

                            return Maybe.just(new UserEntity(user1));
                        })
                        .flatMap(this::enhanceSourceIdentity)
                        .flatMap(this::enhanceLastIdentityUsed)
                        .flatMap(this::enhanceClient))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateUser",
            summary = "Update a user",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void updateUser(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Parameter(name = "user", required = true) @Valid @NotNull UpdateUser updateUser,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(domain -> userService.update(domain, user, updateUser, authenticatedUser))
                        .map(UserEntity::new))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Path("/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateUserStatus",
            summary = "Update a user status",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User status successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void updateUserStatus(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Parameter(name = "status", required = true) @Valid @NotNull StatusEntity status,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(domain -> userService.updateStatus(domain, user, status.isEnabled(), authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @PATCH
    @Path("/username")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateUsername",
            summary = "Update a user username",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User username successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void updateUsername(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String userId,
            @Parameter(name = "username", required = true) @Valid @NotNull UsernameEntity username,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(domain -> userService.updateUsername(domain, userId, username.getUsername().trim(), authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteUser",
            summary = "Delete a user",
            description = "User must have the DOMAIN_USER[DELETE] permission on the specified domain " +
                    "or DOMAIN_USER[DELETE] permission on the specified environment " +
                    "or DOMAIN_USER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.DELETE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(domain -> userService.delete(domain, user, authenticatedUser)))
                .subscribe(u -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("resetPassword")
    @Operation(
            operationId = "resetPassword",
            summary = "Reset password",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void resetPassword(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Parameter(name = "password", required = true) @Valid @NotNull PasswordValue password,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(domain -> userService.resetPassword(domain, user, password.getPassword(), authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);

    }

    @POST
    @Path("sendRegistrationConfirmation")
    @Operation(
            operationId = "sendRegistrationConfirmation",
            summary = "Send registration confirmation email",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email sent"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void sendRegistrationConfirmation(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(domain -> userService.sendRegistrationConfirmation(domain, user, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("lock")
    @Operation(
            operationId = "lockUser",
            summary = "Lock a user",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User locked"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void lockUser(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(domain -> userService.lock(domain, user, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("unlock")
    @Operation(
            operationId = "unlockUser",
            summary = "Unlock a user",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User unlocked"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void unlockUser(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(domain -> userService.unlock(domain, user, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @Path("consents")
    public UserConsentsResource getUserConsentsResource() {
        return resourceContext.getResource(UserConsentsResource.class);
    }

    @Path("roles")
    public UserRolesResource getUserRolesResource() {
        return resourceContext.getResource(UserRolesResource.class);
    }

    @Path("factors")
    public UserFactorsResource getUserFactorsResource() {
        return resourceContext.getResource(UserFactorsResource.class);
    }

    @Path("credentials")
    public UserCredentialsResource getUserCredentialsResource() {
        return resourceContext.getResource(UserCredentialsResource.class);
    }

    @Path("cert-credentials")
    public UserCertCredentialsResource getUserCertCredentialsResource() {
        return resourceContext.getResource(UserCertCredentialsResource.class);
    }

    @Path("devices")
    public DevicesResource getUserDevicesResource() {
        return resourceContext.getResource(DevicesResource.class);
    }

    @Path("audits")
    public UserAuditsResource getUserAuditsResource() {
        return resourceContext.getResource(UserAuditsResource.class);
    }

    @Path("identities")
    public UserIdentitiesResource getUserIdentitiesResource() {
        return resourceContext.getResource(UserIdentitiesResource.class);
    }

    private Maybe<UserEntity> enhanceSourceIdentity(UserEntity userEntity) {
        if (userEntity.getSource() == null) {
            return Maybe.just(userEntity);
        }

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
                            }).defaultIfEmpty(userEntity)
                            .toMaybe();
                })
                .defaultIfEmpty(userEntity)
                .toMaybe();
    }

    private Maybe<UserEntity> enhanceLastIdentityUsed(UserEntity userEntity) {
        if (userEntity.getLastIdentityUsed() == null) {
            return Maybe.just(userEntity);
        }

        if (userEntity.getLastIdentityUsed().equals(userEntity.getSourceId())) {
            userEntity.setLastIdentityUsed(userEntity.getSource());
            return Maybe.just(userEntity);
        }

        return identityProviderService.findById(userEntity.getLastIdentityUsed())
                .map(idP -> {
                    userEntity.setLastIdentityUsed(idP.getName());
                    return userEntity;
                })
                .defaultIfEmpty(userEntity)
                .toMaybe();
    }

    private Maybe<UserEntity> enhanceClient(UserEntity userEntity) {
        if (userEntity.getClient() != null) {
            return applicationService.findById(userEntity.getClient())
                    .switchIfEmpty(Maybe.defer(() -> applicationService.findByDomainAndClientId(userEntity.getReferenceId(), userEntity.getClient())))
                    .map(application -> {
                        userEntity.setApplicationEntity(new ApplicationEntity(application));
                        return userEntity;
                    })
                    .defaultIfEmpty(userEntity)
                    .toMaybe();
        }
        return Maybe.just(userEntity);
    }
}
