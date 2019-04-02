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

import io.gravitee.am.model.Group;
import io.gravitee.am.model.User;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.am.service.model.UpdateGroup;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
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
public class GroupResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private GroupService groupService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a group")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Group successfully fetched", response = Group.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("domain") String domain,
            @PathParam("group") String group,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(irrelevant -> groupService.findById(group))
                .switchIfEmpty(Maybe.error(new GroupNotFoundException(group)))
                .flatMap(group1 -> {
                    if (!group1.getDomain().equalsIgnoreCase(domain)) {
                        throw new BadRequestException("Group does not belong to domain");
                    }
                    return Maybe.just(group1);
                })
                .map(group1 -> Response.ok(group1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a group")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Group successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateUser(
            @PathParam("domain") String domain,
            @PathParam("group") String group,
            @ApiParam(name = "group", required = true) @Valid @NotNull UpdateGroup updateGroup,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> groupService.update(domain, group, updateGroup, authenticatedUser))
                .map(user1 -> Response.ok(user1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Delete a group")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Group successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(@PathParam("domain") String domain,
                           @PathParam("group") String group,
                           @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapCompletable(irrelevant -> groupService.delete(group, authenticatedUser))
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }

    @Path("members")
    public GroupMembersResource getGroupMembersResource() {
        return resourceContext.getResource(GroupMembersResource.class);
    }
}
