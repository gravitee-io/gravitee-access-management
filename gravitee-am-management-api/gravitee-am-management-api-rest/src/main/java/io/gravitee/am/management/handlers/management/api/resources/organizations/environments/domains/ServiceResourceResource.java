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
import io.gravitee.am.management.service.ServiceResourceServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.FactorNotFoundException;
import io.gravitee.am.service.model.UpdateServiceResource;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ServiceResourceResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ServiceResourceServiceProxy resourceService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a resource",
            nickname = "getResource",
            notes = "User must have the DOMAIN_RESOURCE[READ] permission on the specified domain " +
                    "or DOMAIN_RESOURCE[READ] permission on the specified environment " +
                    "or DOMAIN_RESOURCE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Resource successfully fetched", response = ServiceResource.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("resource") String resource,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_RESOURCE, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> resourceService.findById(resource))
                        .switchIfEmpty(Maybe.error(new FactorNotFoundException(resource)))
                        .map(res1 -> {
                            if (!res1.getReferenceId().equalsIgnoreCase(domain) && !res1.getReferenceType().equals(ReferenceType.DOMAIN)) {
                                throw new BadRequestException("Resource does not belong to domain");
                            }
                            return Response.ok(res1).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a resource",
            nickname = "updateResource",
            notes = "User must have the DOMAIN_RESOURCE[UPDATE] permission on the specified domain " +
                    "or DOMAIN_RESOURCE[UPDATE] permission on the specified environment " +
                    "or DOMAIN_RESOURCE[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Resource successfully updated", response = ServiceResource.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("resource") String resource,
            @ApiParam(name = "identity", required = true) @Valid @NotNull UpdateServiceResource updateResource,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_RESOURCE, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> resourceService.update(domain, resource, updateResource, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete a resource",
            nickname = "deleteResource",
            notes = "User must have the DOMAIN_RESOURCE[DELETE] permission on the specified domain " +
                    "or DOMAIN_RESOURCE[DELETE] permission on the specified environment " +
                    "or DOMAIN_RESOURCE[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Resource successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("resource") String resource,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_RESOURCE, Acl.DELETE)
                .andThen(resourceService.delete(domain, resource, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
