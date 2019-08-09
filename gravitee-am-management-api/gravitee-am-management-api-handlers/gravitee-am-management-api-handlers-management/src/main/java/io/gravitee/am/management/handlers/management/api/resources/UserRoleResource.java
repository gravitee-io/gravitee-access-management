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
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserRoleResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private UserService userService;

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Roles successfully revoked", response = User.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@PathParam("domain") String domain,
                     @PathParam("user") String user,
                     @PathParam("role") String role,
                     @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(endUser -> userService.revokeRoles(user, Collections.singletonList(role), authenticatedUser))
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }
}
