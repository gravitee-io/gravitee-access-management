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
import io.gravitee.am.management.service.ManagementUserService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

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
    private ManagementUserService userService;

    @DELETE
    @Operation(summary = "Revoke user factor",
            operationId = "deleteUserFactor",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User factor successfully revoked"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})

    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @PathParam("factor") String factor,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(domain -> userService.findById(domain, user)
                                .switchIfEmpty(Maybe.error(new UserNotFoundException(user)))
                                .flatMapSingle(user1 -> {
                                    if (user1.getFactors() != null) {
                                        List<EnrolledFactor> enrolledFactors = user1.getFactors()
                                                .stream()
                                                .filter(enrolledFactor -> !factor.equals(enrolledFactor.getFactorId()))
                                                .collect(Collectors.toList());
                                        return userService.enrollFactors(domain, user, enrolledFactors, authenticatedUser);
                                    }
                                    return Single.just(user1);
                                })))
                .subscribe(__ -> response.resume(Response.noContent().build()), response::resume);
    }
}
