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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import io.gravitee.am.common.scim.parser.SCIMFilterParser;
import io.gravitee.am.management.handlers.management.api.bulk.BulkOperationResult;
import io.gravitee.am.management.handlers.management.api.bulk.BulkRequest;
import io.gravitee.am.management.handlers.management.api.bulk.BulkResponse;
import io.gravitee.am.management.handlers.management.api.model.UserEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.schemas.BulkCreateOrganizationUser;
import io.gravitee.am.management.handlers.management.api.schemas.BulkDeleteUser;
import io.gravitee.am.management.handlers.management.api.schemas.BulkUpdateUser;
import io.gravitee.am.management.handlers.management.api.spring.UserBulkConfiguration;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.TooManyOperationsException;
import io.gravitee.am.service.model.NewOrganizationUser;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import javax.inject.Named;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "user")
public class OrganizationUsersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private UserBulkConfiguration userBulkConfiguration;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private OrganizationService organizationService;
    
    @Autowired
    private ObjectMapper mapper;

    @Autowired
    @Named("managementOrganizationUserService")
    protected OrganizationUserService organizationUserService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listOrganisationUsers",
            summary = "List users of the organization",
            description = "User must have the ORGANIZATION_USER[LIST] permission on the specified organization. " +
                    "Each returned user is filtered and contains only basic information such as id and username and displayname. " +
                    "Last login and identity provider name will be also returned if current user has ORGANIZATION_USER[READ] permission on the organization.")

    @ApiResponse(responseCode = "200", description = "List users of the organization",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Page.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void list(
            @PathParam("organizationId") String organizationId,
            @QueryParam("q") String query,
            @QueryParam("filter") String filter,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @Max(1000) @Min(1) @DefaultValue("30") int size,
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
            summary = "Create a platform user or Service Account",
            description = "User must have the ORGANIZATION_USER[READ] permission on the specified organization")

    @ApiResponse(responseCode = "201", description = "User or Service Account successfully created",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = User.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void create(
            @PathParam("organizationId") String organizationId,
            @Parameter(name = "user", required = true) @Valid @NotNull final NewOrganizationUser newOrganizationUser,
            @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.CREATE)
                .andThen(organizationService.findById(organizationId)
                        .flatMap(organization -> organizationUserService.createGraviteeUser(organization, newOrganizationUser, authenticatedUser))
                        .map(user -> Response
                                .created(URI.create("/organizations/" + organizationId + "/users/" + user.getId()))
                                .entity(new UserEntity(user))
                                .build()))
                .subscribe(response::resume, response::resume);
    }


    protected Single<Page<User>> searchUsers(ReferenceType referenceType,
                                             String referenceId,
                                             String query,
                                             String filter,
                                             int page,
                                             int size) {
        return executeSearchUsers(organizationUserService, referenceType, referenceId, query, filter, page, size);
    }

    private Single<Page<User>> executeSearchUsers(OrganizationUserService service, ReferenceType referenceType, String referenceId, String query, String filter, int page, int size) {
        if (query != null) {
            return service.search(referenceType, referenceId, query, page, size);
        }
        if (filter != null) {
            return Single.defer(() -> {
                FilterCriteria filterCriteria = FilterCriteria.convert(SCIMFilterParser.parse(filter));
                return service.search(referenceType, referenceId, filterCriteria, page, size);
            }).onErrorResumeNext(ex -> {
                if (ex instanceof IllegalArgumentException) {
                    return Single.error(new BadRequestException(ex.getMessage()));
                }
                return Single.error(ex);
            });
        }
        return service.findAll(referenceType, referenceId, page, size);
    }

    @POST
    @Path("/bulk")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "bulkOrganisationUserOperation",
            summary = "Create/update/delete platform users or Service Accounts",
            description = "User must have the ORGANIZATION_USER[CREATE/UPDATE/DELETE] permission on the specified organization")

    @ApiResponse(responseCode = "200", description = "Some users or Service Accounts got created, inspect each result for details",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BulkResponse.class)))
    @ApiResponse(responseCode = "201", description = "Users or Service Accounts successfully created",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BulkResponse.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void handleBulkOperation(
            @PathParam("organizationId") String organizationId,
            @Valid @NotNull final OrganizationUserBulkRequest bulkRequest,
            @Suspended final AsyncResponse response) {

        if (bulkRequest.items().size() > userBulkConfiguration.bulkMaxRequestOperations()) {
            throw TooManyOperationsException.tooManyOperation(userBulkConfiguration.bulkMaxRequestOperations());
        }

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, bulkRequest.action().requiredAcl())
                .andThen(organizationService.findById(organizationId)
                        .flatMap(organization -> processBulkRequest(bulkRequest, organization, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }


    private SingleSource<?> processBulkRequest(BulkRequest.Generic bulkRequest, Organization organization, io.gravitee.am.identityprovider.api.User authenticatedUser) {
        return switch (bulkRequest.action()) {
            case CREATE ->
                    bulkRequest.processOneByOne(NewOrganizationUser.class, mapper, user -> organizationUserService.createGraviteeUser(organization, user, authenticatedUser)
                            .map(BulkOperationResult::created)
                    );
            case DELETE ->
                    bulkRequest.processOneByOne(String.class, mapper, id -> organizationUserService.delete(ReferenceType.ORGANIZATION, organization.getId(), id, authenticatedUser)
                            .map(User::getId)
                            .map(BulkOperationResult::ok)
                    );
            case UPDATE ->
                    bulkRequest.processOneByOne(BulkUpdateUser.UpdateUserWithId.class, mapper, updateUser -> organizationUserService.update(ReferenceType.ORGANIZATION, organization.getId(), updateUser.getId(), updateUser, authenticatedUser)
                            .map(UserEntity::new)
                            .map(BulkOperationResult::ok)
                    );
        };


    }

    @Path("{user}")
    public OrganizationUserResource getUserResource() {
        return resourceContext.getResource(OrganizationUserResource.class);
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

    @Schema(oneOf = {BulkCreateOrganizationUser.class, BulkUpdateUser.class, BulkDeleteUser.class},
            discriminatorProperty = "action",
            discriminatorMapping = {
                    @DiscriminatorMapping(value = "CREATE", schema = BulkCreateOrganizationUser.class),
                    @DiscriminatorMapping(value = "UPDATE", schema = BulkUpdateUser.class),
                    @DiscriminatorMapping(value = "DELETE", schema = BulkDeleteUser.class)
            })
    private static class OrganizationUserBulkRequest extends BulkRequest.Generic {
        @JsonCreator
        public OrganizationUserBulkRequest(@JsonProperty("action") Action action,
                                     @JsonProperty("failOnErrors") Integer failOnErrors,
                                     @JsonProperty("items") List<BaseJsonNode> items) {
            super(action, failOnErrors, items);
        }
    }
}
