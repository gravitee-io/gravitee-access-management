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

import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
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
import java.util.Comparator;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"user"})
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
    private IdentityProviderService identityProviderService;

    @Autowired
    private PasswordValidator passwordValidator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List users for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List users for a security domain", response = User.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@PathParam("domain") String domain,
                                @QueryParam("q") String query,
                                @QueryParam("page") @DefaultValue("0") int page,
                                @QueryParam("size") @DefaultValue(MAX_USERS_SIZE_PER_PAGE_STRING) int size,
                                @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> {
                    if (query != null) {
                        return userService.search(domain, query, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
                    } else {
                        return userService.findByDomain(domain, page, Integer.min(size, MAX_USERS_SIZE_PER_PAGE));
                    }
                })
                .flatMap(pagedUsers ->
                        Observable.fromIterable(pagedUsers.getData())
                            .flatMapSingle(user -> {
                                if (user.getSource() != null) {
                                    return identityProviderService.findById(user.getSource())
                                            .map(idP -> {
                                                user.setSource(idP.getName());
                                                return user;
                                            })
                                            .defaultIfEmpty(user)
                                            .toSingle();
                                }
                                return Single.just(user);
                            })
                            .toSortedList(Comparator.comparing(User::getUsername))
                            .map(users -> new Page(users, pagedUsers.getCurrentPage(), pagedUsers.getTotalCount()))
                )
                .map(users -> Response.ok(users).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a user")
    @ApiResponses({
            @ApiResponse(code = 201, message = "User successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("domain") String domain,
            @ApiParam(name = "user", required = true)
            @Valid @NotNull final NewUser newUser,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        // user must have a password in no pre registration mode
        if (!newUser.isPreRegistration() && newUser.getPassword() == null) {
            response.resume(new UserInvalidException(("Field [password] is required")));
            return;
        }

        // check password policy
        if (newUser.getPassword() != null) {
            if (!passwordValidator.validate(newUser.getPassword())) {
                response.resume(new UserInvalidException(("Field [password] is invalid")));
                return;
            }
        }

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(userProvider -> userService.create(domain, newUser, authenticatedUser))
                .map(user -> Response
                        .created(URI.create("/domains/" + domain + "/users/" + user.getId()))
                        .entity(user)
                        .build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{user}")
    public UserResource getUserResource() {
        return resourceContext.getResource(UserResource.class);
    }
}
