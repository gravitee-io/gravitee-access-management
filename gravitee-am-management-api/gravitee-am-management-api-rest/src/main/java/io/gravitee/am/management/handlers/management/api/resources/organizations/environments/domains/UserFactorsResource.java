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

import io.gravitee.am.management.handlers.management.api.model.EnrolledFactorEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.util.Collections;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

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
    @ApiOperation(value = "Get a user enrolled factors",
            notes = "User must have the DOMAIN_USER[READ] permission on the specified domain " +
                    "or DOMAIN_USER[READ] permission on the specified environment " +
                    "or DOMAIN_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User enrolled factors successfully fetched", response = EnrolledFactor.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.READ)
                .andThen(domainService.findById(domain)
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
                                                    .toMaybe()
                                    )
                                    .toList();
                        }))
                .subscribe(response::resume, response::resume);
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
