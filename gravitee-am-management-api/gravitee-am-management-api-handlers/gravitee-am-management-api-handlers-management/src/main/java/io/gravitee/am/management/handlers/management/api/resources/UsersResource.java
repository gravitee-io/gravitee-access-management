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

import io.gravitee.am.management.handlers.management.api.resources.enhancer.UserEnhancer;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain", "users"})
public class UsersResource extends AbstractResource {

    private static final int MAX_USERS_SIZE_PER_PAGE = 30;
    private static final String MAX_USERS_SIZE_PER_PAGE_STRING = "30";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private UserService userService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private UserEnhancer userEnhancer;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List users for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List users for a security domain", response = User.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Page<User> listUsers(@PathParam("domain") String domain,
                                @QueryParam("page") @DefaultValue("0") int page,
                                @QueryParam("size") @DefaultValue(MAX_USERS_SIZE_PER_PAGE_STRING) int size) {
        domainService.findById(domain);

        Page<User> pagedUsers = userService.findByDomain(domain, page, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
        // enhance users
        pagedUsers.getData().stream().forEach(u -> userEnhancer.enhance().apply(u));

        return pagedUsers;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a user")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response createUser(
            @PathParam("domain") String domain,
            @ApiParam(name = "user", required = true)
            @Valid @NotNull final NewUser newUser) {
   /*     domainService.findById(domain);

        User user = userService.create(domain, newUser);
        if (user != null) {
            return Response
                    .created(URI.create("/domains/" + domain + "/users/" + user.getId()))
                    .entity(user)
                    .build();
        }

        return Response.serverError().build();*/

        return Response.status(Response.Status.NOT_IMPLEMENTED).entity("Not implemented").build();
    }

    @Path("{user}")
    public UserResource getUserResource() {
        return resourceContext.getResource(UserResource.class);
    }
}
