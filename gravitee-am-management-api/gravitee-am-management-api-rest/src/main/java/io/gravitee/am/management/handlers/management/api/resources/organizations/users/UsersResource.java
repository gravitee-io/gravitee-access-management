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

import io.gravitee.am.management.handlers.management.api.model.UserEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractUsersResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "user")})
public class UsersResource extends AbstractUsersResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private OrganizationService organizationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listOrganisationUsers",
            summary = "List users of the organization",
            description = "User must have the ORGANIZATION_USER[LIST] permission on the specified organization. " +
                    "Each returned user is filtered and contains only basic information such as id and username and displayname. " +
                    "Last login and identity provider name will be also returned if current user has ORGANIZATION_USER[READ] permission on the organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List users of the organization",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @QueryParam("q") String query,
            @QueryParam("filter") String filter,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_USERS_SIZE_PER_PAGE_STRING) int size,
            @Suspended final AsyncResponse response) {

        io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        permissionService.findAllPermissions(authenticatedUser, ReferenceType.ORGANIZATION, organizationId)
                .flatMap(organizationPermissions ->
                        checkPermission(organizationPermissions, Permission.ORGANIZATION_USER, Acl.LIST)
                                .andThen(searchUsers(ReferenceType.ORGANIZATION, organizationId, query, filter, page, size)
                                        .flatMap(pagedUsers ->
                                                Observable.fromIterable(pagedUsers.getData())
                                                        .flatMapSingle(user -> filterUserInfos(organizationPermissions, user))
                                                        .toSortedList(Comparator.comparing(User::getUsername, Comparator.nullsLast(Comparator.naturalOrder())))
                                                        .map(users -> new Page<>(users, pagedUsers.getCurrentPage(), pagedUsers.getTotalCount())))))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createOrganisationUser",
            summary = "Create a platform user",
            description = "User must have the ORGANIZATION_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @Parameter(name = "user", required = true) @Valid @NotNull final NewUser newUser,
            @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.CREATE)
                .andThen(organizationService.findById(organizationId)
                        .flatMap(organization -> organizationUserService.createGraviteeUser(organization, newUser, authenticatedUser))
                        .map(user -> Response
                                .created(URI.create("/organizations/" + organizationId + "/users/" + user.getId()))
                                .entity(new UserEntity(user))
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{user}")
    public UserResource getUserResource() {
        return resourceContext.getResource(UserResource.class);
    }

    private Single<User> filterUserInfos(Map<Permission, Set<Acl>> organizationPermissions, User user) {

        User filteredUser;

        if (hasPermission(organizationPermissions, Permission.ORGANIZATION_USER, Acl.READ)) {
            // Current user has read permission, copy all information.
            filteredUser = new UserEntity(user);
            if (user.getSource() != null) {
                return identityProviderService.findById(user.getSource())
                        .map(idP -> {
                            filteredUser.setSource(idP.getName());
                            return filteredUser;
                        })
                        .defaultIfEmpty(filteredUser);
            }
        } else {
            // Current user doesn't have read permission, select only little information and remove default values that could be inexact.
            filteredUser = new User(false);
            filteredUser.setId(user.getId());
            filteredUser.setUsername(user.getUsername());
            filteredUser.setDisplayName(user.getDisplayName());
            filteredUser.setPicture(user.getPicture());
        }
        return Single.just(filteredUser);
    }
}
