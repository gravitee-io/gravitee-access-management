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
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

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
    private UserService userService;

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
    @ApiOperation(
            nickname = "findUser",
            value = "Get a user",
            notes = "User must have the DOMAIN_USER[READ] permission on the specified domain " +
                    "or DOMAIN_USER[READ] permission on the specified environment " +
                    "or DOMAIN_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User successfully fetched", response = UserEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> userService.findById(user))
                        .switchIfEmpty(Maybe.error(new UserNotFoundException(user)))
                        .flatMap(user1 -> {
                            if (user1.getReferenceType() == ReferenceType.DOMAIN
                                    && !user1.getReferenceId().equalsIgnoreCase(domain)) {
                                throw new BadRequestException("User does not belong to domain");
                            }

                            return Maybe.just(new UserEntity(user1));
                        })
                        .flatMap(this::enhanceIdentityProvider)
                        .flatMap(this::enhanceClient))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "updateUser",
            value = "Update a user",
            notes = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateUser(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @ApiParam(name = "user", required = true) @Valid @NotNull UpdateUser updateUser,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> userService.update(ReferenceType.DOMAIN, domain, user, updateUser, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Path("/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "updateUserStatus",
            value = "Update a user status",
            notes = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User status successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateUserStatus(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @ApiParam(name = "status", required = true) @Valid @NotNull StatusEntity status,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> userService.updateStatus(ReferenceType.DOMAIN, domain, user, status.isEnabled(), authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @PATCH
    @Path("/username")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "updateUsername",
            value = "Update a user username",
            notes = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User username successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateUsername(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String userId,
            @ApiParam(name = "username", required = true) @Valid @NotNull UsernameEntity username,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> userService.updateUsername(ReferenceType.DOMAIN, domain, userId, username.getUsername(), authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(
            nickname = "deleteUser",
            value = "Delete a user",
            notes = "User must have the DOMAIN_USER[DELETE] permission on the specified domain " +
                    "or DOMAIN_USER[DELETE] permission on the specified environment " +
                    "or DOMAIN_USER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "User successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.DELETE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapCompletable(irrelevant -> userService.delete(ReferenceType.DOMAIN, domain, user, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("resetPassword")
    @ApiOperation(
            nickname = "resetPassword",
            value = "Reset password",
            notes = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Password reset"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void resetPassword(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @ApiParam(name = "password", required = true) @Valid @NotNull PasswordValue password,
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
    @ApiOperation(
            nickname = "sendRegistrationConfirmation",
            value = "Send registration confirmation email",
            notes = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Email sent"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void sendRegistrationConfirmation(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(userService.sendRegistrationConfirmation(domain, user, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("lock")
    @ApiOperation(
            nickname = "lockUser",
            value = "Lock a user",
            notes = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "User locked"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void lockUser(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapCompletable(irrelevant -> userService.lock(ReferenceType.DOMAIN, domain, user, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("unlock")
    @ApiOperation(
            nickname = "unlockUser",
            value = "Unlock a user",
            notes = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "User unlocked"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void unlockUser(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapCompletable(irrelevant -> userService.unlock(ReferenceType.DOMAIN, domain, user, authenticatedUser)))
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

    @Path("devices")
    public DevicesResource getUserDevicesResource() {
        return resourceContext.getResource(DevicesResource.class);
    }

    @Path("audits")
    public UserAuditsResource getUserAuditsResource() {
        return resourceContext.getResource(UserAuditsResource.class);
    }

    private Maybe<UserEntity> enhanceIdentityProvider(UserEntity userEntity) {
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
                                }).defaultIfEmpty(userEntity)
                                .toMaybe();
                    })
                    .defaultIfEmpty(userEntity)
                    .toMaybe();
        }
        return Maybe.just(userEntity);
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
