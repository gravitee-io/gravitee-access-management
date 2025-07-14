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
import io.gravitee.am.management.handlers.management.api.schemas.BulkCreateUser;
import io.gravitee.am.management.handlers.management.api.schemas.BulkDeleteUser;
import io.gravitee.am.management.handlers.management.api.schemas.BulkUpdateUser;
import io.gravitee.am.management.handlers.management.api.spring.UserBulkConfiguration;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.management.service.ManagementUserService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.TooManyOperationsException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
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

import java.net.URI;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "user")
public class UsersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private UserBulkConfiguration userBulkConfiguration;

    @Autowired
    private IdentityProviderServiceProxy identityProviderService;

    @Autowired
    protected ManagementUserService userService;

    @Autowired
    protected DomainService domainService;

    @Autowired
    ObjectMapper objectMapper;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listUsers",
            summary = "List users for a security domain",
            description = "User must have the DOMAIN_USER[LIST] permission on the specified domain " +
                    "or DOMAIN_USER[LIST] permission on the specified environment " +
                    "or DOMAIN_USER[LIST] permission on the specified organization. " +
                    "Each returned user is filtered and contains only basic information such as id and username and displayname. " +
                    "Last login and identity provider name will be also returned if current user has DOMAIN_USER[READ] permission on the domain, environment or organization.")

    @ApiResponse(responseCode = "200", description = "List users for a security domain",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserPage.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @QueryParam("q") String query,
            @QueryParam("filter") String filter,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @Max(1000) @Min(1) @DefaultValue("30") int size,
            @Suspended final AsyncResponse response) {

        io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.LIST)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)))
                        .flatMap(domain -> searchUsers(domain, query, filter, page, size))
                        .flatMap(pagedUsers ->
                                hasAnyPermission(authenticatedUser, organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.READ)
                                        .flatMap(hasPermission -> Observable.fromIterable(pagedUsers.getData())
                                                .flatMapSingle(user -> filterUserInfos(hasPermission, user))
                                                .toSortedList(Comparator.comparing(User::getUsername, Comparator.nullsLast(Comparator.naturalOrder())))
                                                .map(users -> new UserPage(users, pagedUsers.getCurrentPage(), pagedUsers.getTotalCount())))))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createUser",
            summary = "Create a user on the specified security domain",
            description = "User must have the DOMAIN_USER[CREATE] permission on the specified domain " +
                    "or DOMAIN_USER[CREATE] permission on the specified environment " +
                    "or DOMAIN_USER[CREATE] permission on the specified organization")
    @ApiResponse(responseCode = "201", description = "User successfully created",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = User.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "user", required = true)
            @Valid @NotNull final NewUser newUser,
            @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.CREATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(domain -> userService.create(domain, newUser, authenticatedUser))
                        .map(user -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domainId + "/users/" + user.getId()))
                                .entity(user)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("/bulk")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "bulkUserOperation",
            summary = "Create/update/delete multiple users on the specified security domain",
            description = "User must have the DOMAIN_USER[CREATE/UPDATE/DELETE] permission on the specified domain, " +
                    "the environment, or the organization"
    )
    @ApiResponse(responseCode = "200", description = "Some users got created, inspect each result for details",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BulkResponse.class)))
    @ApiResponse(responseCode = "201", description = "All users successfully created",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BulkResponse.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void handleBulkOperation(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Valid @NotNull final DomainUserBulkRequest bulkRequest,
            @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();
        var requiredAcl = bulkRequest.action().requiredAcl();

        if(bulkRequest.items().size() > userBulkConfiguration.bulkMaxRequestOperations()){
            throw TooManyOperationsException.tooManyOperation(userBulkConfiguration.bulkMaxRequestOperations());
        }

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, requiredAcl)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(domain -> processBulkRequest(bulkRequest, domain, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    protected Single<Page<User>> searchUsers(Domain domain, String query, String filter, int page, int size) {
        if (query != null) {
            return userService.search(domain, query, page, size);
        }
        if (filter != null) {
            return Single.defer(() -> {
                FilterCriteria filterCriteria = FilterCriteria.convert(SCIMFilterParser.parse(filter));
                return userService.search(domain, filterCriteria, page, size);
            }).onErrorResumeNext(ex -> {
                if (ex instanceof IllegalArgumentException) {
                    return Single.error(new BadRequestException(ex.getMessage()));
                }
                return Single.error(ex);
            });
        }
        return userService.findAll(domain, page, size);
    }

    private Single<?> processBulkRequest(BulkRequest.Generic bulkRequest, Domain domain, io.gravitee.am.identityprovider.api.User authenticatedUser) {
        return switch (bulkRequest.action()) {
            case CREATE ->
                    bulkRequest.processOneByOne(NewUser.class, objectMapper, newUser -> userService.create(domain, newUser, authenticatedUser)
                            .map(BulkOperationResult::created));
            case DELETE ->
                    bulkRequest.processOneByOne(String.class, objectMapper, id -> userService.delete(domain, id, authenticatedUser)
                            .map(User::getId)
                            .map(BulkOperationResult::ok)
                    );
            case UPDATE ->
                    bulkRequest.processOneByOne(BulkUpdateUser.UpdateUserWithId.class, objectMapper, updated -> userService.update(domain, updated.getId(), updated, authenticatedUser)
                            .map(UserEntity::new)
                            .map(BulkOperationResult::ok)
                    );
        };
    }

    @Path("{user}")
    public UserResource getUserResource() {
        return resourceContext.getResource(UserResource.class);
    }

    private Single<User> filterUserInfos(Boolean hasPermission, User user) {
        User filteredUser;
        if (hasPermission) {
            // Current user has read permission, copy all information.
            filteredUser = new User(user);
            if (user.getSource() != null) {
                return identityProviderService.findById(user.getSource())
                        .map(idP -> {
                            filteredUser.setSource(idP.getName());
                            return filteredUser;
                        })
                        .defaultIfEmpty(filteredUser);
            }
        } else {
            // Current user doesn't have read permission, select only few information and remove default values that could be inexact.
            filteredUser = new User(false);
            filteredUser.setId(user.getId());
            filteredUser.setUsername(user.getUsername());
            filteredUser.setEnabled(user.isEnabled());
            filteredUser.setDisplayName(user.getDisplayName());
            filteredUser.setPicture(user.getPicture());
        }

        return Single.just(filteredUser);
    }

    public static final class UserPage extends Page<User> {
        public UserPage(Collection<User> data, int currentPage, long totalCount) {
            super(data, currentPage, totalCount);
        }
    }

    @Schema(oneOf = {BulkCreateUser.class, BulkUpdateUser.class, BulkDeleteUser.class},
            discriminatorProperty = "action",
            discriminatorMapping = {
                    @DiscriminatorMapping(value = "CREATE", schema = BulkCreateUser.class),
                    @DiscriminatorMapping(value = "UPDATE", schema = BulkUpdateUser.class),
                    @DiscriminatorMapping(value = "DELETE", schema = BulkDeleteUser.class)
            })
    private static class DomainUserBulkRequest extends BulkRequest.Generic {
        @JsonCreator
        public DomainUserBulkRequest(@JsonProperty("action") Action action,
                                     @JsonProperty("failOnErrors") Integer failOnErrors,
                                     @JsonProperty("items") List<BaseJsonNode> items) {
            super(action, failOnErrors, items);
        }
    }
}
