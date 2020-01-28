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

import io.gravitee.am.management.handlers.management.api.model.EnrolledFactorEntity;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserFactorsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private UserService userService;

    @Autowired
    private FactorService factorService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a user enrolled factors")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User enrolled factors successfully fetched", response = EnrolledFactor.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_USER, acls = RolePermissionAction.READ)
    })
    public void list(@PathParam("domain") String domain,
                     @PathParam("user") String user,
                     @Suspended final AsyncResponse response) {

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(__ -> userService.findById(user))
                .switchIfEmpty(Maybe.error(new UserNotFoundException(user)))
                .flatMapSingle(user1 -> {
                    if (user1.getFactors() == null) {
                        return Single.just(Collections.emptyList());
                    }
                    return Observable.fromIterable(user1.getFactors())
                            .flatMapMaybe(enrolledFactor ->
                                    factorService.findById(enrolledFactor.getFactorId())
                                            .map(factor -> {
                                                EnrolledFactorEntity enrolledFactorEntity = new EnrolledFactorEntity(enrolledFactor);
                                                enrolledFactorEntity.setType(factor.getType());
                                                enrolledFactorEntity.setName(factor.getName());
                                                return enrolledFactorEntity;
                                            })
                                            .defaultIfEmpty(unknown(enrolledFactor))
                            )
                            .toList();
                })
                .map(factors ->  Response.ok(factors).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{factor}")
    public UserFactorResource getUserFactorResource() {
        return resourceContext.getResource(UserFactorResource.class);
    }

    private EnrolledFactorEntity unknown(EnrolledFactor enrolledFactor) {
        EnrolledFactorEntity enrolledFactorEntity = new EnrolledFactorEntity(enrolledFactor);
        enrolledFactorEntity.setName("Deleted factor");
        enrolledFactorEntity.setType("Unknown factor type");
        return enrolledFactorEntity;
    }
}
