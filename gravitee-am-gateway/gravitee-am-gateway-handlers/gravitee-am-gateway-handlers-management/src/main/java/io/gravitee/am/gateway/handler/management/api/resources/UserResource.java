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
package io.gravitee.am.gateway.handler.management.api.resources;

import io.gravitee.am.gateway.handler.management.api.resources.enhancer.UserEnhancer;
import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.gateway.service.exception.DomainNotFoundException;
import io.gravitee.am.gateway.service.model.UpdateUser;
import io.gravitee.am.model.User;
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
@Api(tags = {"domain", "user"})
public class UserResource {

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
    @ApiOperation(value = "Get a user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User successfully fetched", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response get(
            @PathParam("domain") String domain,
            @PathParam("user") String user) throws DomainNotFoundException {
        domainService.findById(domain);

        User user1 = userEnhancer.enhance().apply(userService.findById(user));
        if (!user1.getDomain().equalsIgnoreCase(domain)) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("User does not belong to domain")
                    .build();
        }
        return Response.ok(user1).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a user")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User successfully updated", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response updateUser(
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @ApiParam(name = "user", required = true) @Valid @NotNull UpdateUser updateUser) {
        /*domainService.findById(domain);

        return userService.update(domain, user, updateUser);*/

        return Response.status(Response.Status.NOT_IMPLEMENTED).entity("Not implemented").build();
    }

    @DELETE
    @ApiOperation(value = "Delete a user")
    @ApiResponses({
            @ApiResponse(code = 204, message = "User successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response delete(@PathParam("domain") String domain, @PathParam("user") String user) {
        /*userService.delete(user);

        return Response.noContent().build();*/

        return Response.status(Response.Status.NOT_IMPLEMENTED).entity("Not implemented").build();
    }
}
