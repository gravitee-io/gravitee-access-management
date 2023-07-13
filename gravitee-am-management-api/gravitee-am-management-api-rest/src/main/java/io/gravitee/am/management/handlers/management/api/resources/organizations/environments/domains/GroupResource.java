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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.am.service.model.UpdateGroup;
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

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private GroupService groupService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "findGroup",
            value = "Get a group",
            notes = "User must have the DOMAIN_GROUP[READ] permission on the specified domain " +
                    "or DOMAIN_GROUP[READ] permission on the specified environment " +
                    "or DOMAIN_GROUP[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Group successfully fetched", response = Group.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("group") String group,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_GROUP, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> groupService.findById(group))
                        .switchIfEmpty(Maybe.error(new GroupNotFoundException(group)))
                        .flatMap(group1 -> {
                            if (group1.getReferenceType() == ReferenceType.DOMAIN
                                    && !group1.getReferenceId().equalsIgnoreCase(domain)) {
                                throw new BadRequestException("Group does not belong to domain");
                            }
                            return Maybe.just(group1);
                        }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "updateGroup",
            value = "Update a group",
            notes = "User must have the DOMAIN_GROUP[UPDATE] permission on the specified domain " +
                    "or DOMAIN_GROUP[UPDATE] permission on the specified environment " +
                    "or DOMAIN_GROUP[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Group successfully updated", response = Group.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateGroup(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("group") String group,
            @ApiParam(name = "group", required = true) @Valid @NotNull UpdateGroup updateGroup,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_GROUP, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> groupService.update(domain, group, updateGroup, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(
            nickname = "deleteGroup",
            value = "Delete a group",
            notes = "User must have the DOMAIN_GROUP[DELETE] permission on the specified domain " +
                    "or DOMAIN_GROUP[DELETE] permission on the specified environment " +
                    "or DOMAIN_GROUP[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Group successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("group") String group,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_GROUP, Acl.DELETE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapCompletable(irrelevant -> groupService.delete(ReferenceType.DOMAIN, domain, group, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @Path("members")
    public GroupMembersResource getGroupMembersResource() {
        return resourceContext.getResource(GroupMembersResource.class);
    }

    @Path("roles")
    public GroupRolesResource getGroupRolesResource() {
        return resourceContext.getResource(GroupRolesResource.class);
    }
}