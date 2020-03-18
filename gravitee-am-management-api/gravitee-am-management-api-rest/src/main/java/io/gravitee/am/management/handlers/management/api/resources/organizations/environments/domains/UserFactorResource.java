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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.DELETE;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserFactorResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private UserService userService;

    @DELETE
    @ApiOperation(value = "Revoke user factor")
    @ApiResponses({
            @ApiResponse(code = 204, message = "User factor successfully revoked"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_USER, acls = RolePermissionAction.UPDATE)
    })
    public void delete(@PathParam("domain") String domain,
                       @PathParam("user") String user,
                       @PathParam("factor") String factor,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(__ -> userService.findById(user))
                .switchIfEmpty(Maybe.error(new UserNotFoundException(user)))
                .flatMapSingle(user1 -> {
                    List<EnrolledFactor> enrolledFactorList = user1.getFactors()
                            .stream()
                            .filter(enrolledFactor -> !factor.equals(enrolledFactor.getFactorId()))
                            .collect(Collectors.toList());
                    user1.setFactors(enrolledFactorList);
                    return userService.enrollFactors(user, enrolledFactorList, authenticatedUser);
                })
                .subscribe(
                        __ -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }
}
