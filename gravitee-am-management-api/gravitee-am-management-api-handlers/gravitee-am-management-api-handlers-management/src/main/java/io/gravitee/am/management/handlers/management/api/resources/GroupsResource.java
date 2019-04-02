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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Group;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewGroup;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"group"})
public class GroupsResource extends AbstractResource {

    private static final int MAX_GROUPS_SIZE_PER_PAGE = 100;
    private static final String MAX_GROUPS_SIZE_PER_PAGE_STRING = "100";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private GroupService groupService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List groups for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List groups for a security domain", response = Group.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@PathParam("domain") String domain,
                                @QueryParam("page") @DefaultValue("0") int page,
                                @QueryParam("size") @DefaultValue(MAX_GROUPS_SIZE_PER_PAGE_STRING) int size,
                                @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> groupService.findByDomain(domain, page, Integer.min(size, MAX_GROUPS_SIZE_PER_PAGE))
                        .map(groups -> Response.ok(groups).build())
                )
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a group")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Group successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("domain") String domain,
            @ApiParam(name = "group", required = true)
            @Valid @NotNull final NewGroup newGroup,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> groupService.create(domain, newGroup, authenticatedUser))
                .map(group -> Response
                        .created(URI.create("/domains/" + domain + "/groups/" + group.getId()))
                        .entity(group)
                        .build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{group}")
    public GroupResource getGroupResource() {
        return resourceContext.getResource(GroupResource.class);
    }
}
