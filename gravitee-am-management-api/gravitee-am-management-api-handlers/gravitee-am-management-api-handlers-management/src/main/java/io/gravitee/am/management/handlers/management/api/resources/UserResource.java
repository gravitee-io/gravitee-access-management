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

import io.gravitee.am.model.User;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.UpdateUser;
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
public class UserResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private UserService userService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a user")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User successfully fetched", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(irrelevant -> userService.findById(user))
                .switchIfEmpty(Maybe.error(new UserNotFoundException(user)))
                .flatMap(user1 -> {
                    if (!user1.getDomain().equalsIgnoreCase(domain)) {
                        throw new BadRequestException("User does not belong to domain");
                    }
                    return Maybe.just(user1);
                })
                .flatMap(user1 -> {
                    if (user1.getSource() != null){
                        return identityProviderService.findById(user1.getSource())
                                .map(idP -> {
                                    user1.setSource(idP.getName());
                                    return user1;
                                })
                                .defaultIfEmpty(user1);
                    }
                    return Maybe.just(user1);
                })
                .map(user1 -> Response.ok(user1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
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
