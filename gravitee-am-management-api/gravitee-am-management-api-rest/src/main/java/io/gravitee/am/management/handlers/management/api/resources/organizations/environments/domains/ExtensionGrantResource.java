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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ExtensionGrantService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.ExtensionGrantNotFoundException;
import io.gravitee.am.service.model.UpdateExtensionGrant;
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

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ExtensionGrantService extensionGrantService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a extension grant",
            notes = "User must have the DOMAIN_EXTENSION_GRANT[READ] permission on the specified domain " +
                    "or DOMAIN_EXTENSION_GRANT[READ] permission on the specified environment " +
                    "or DOMAIN_EXTENSION_GRANT[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Extension grant successfully fetched", response = ExtensionGrant.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("extensionGrant") String extensionGrant,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_EXTENSION_GRANT, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> extensionGrantService.findById(extensionGrant))
                        .switchIfEmpty(Maybe.error(new ExtensionGrantNotFoundException(extensionGrant)))
                        .map(extensionGrant1 -> {
                            if (!extensionGrant1.getDomain().equalsIgnoreCase(domain)) {
                                throw new BadRequestException("Extension grant does not belong to domain");
                            }
                            return Response.ok(extensionGrant1).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a extension grant",
            notes = "User must have the DOMAIN_EXTENSION_GRANT[UPDATE] permission on the specified domain " +
                    "or DOMAIN_EXTENSION_GRANT[UPDATE] permission on the specified environment " +
                    "or DOMAIN_EXTENSION_GRANT[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Extension grant successfully updated", response = ExtensionGrant.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("extensionGrant") String extensionGrant,
            @ApiParam(name = "tokenGranter", required = true) @Valid @NotNull UpdateExtensionGrant updateExtensionGrant,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_EXTENSION_GRANT, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> extensionGrantService.update(domain, extensionGrant, updateExtensionGrant, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete a extension grant",
            notes = "User must have the DOMAIN_EXTENSION_GRANT[DELETE] permission on the specified domain " +
                    "or DOMAIN_EXTENSION_GRANT[DELETE] permission on the specified environment " +
                    "or DOMAIN_EXTENSION_GRANT[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Extension grant successfully deleted"),
            @ApiResponse(code = 400, message = "Extension grant is bind to existing clients"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("extensionGrant") String extensionGrant,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_EXTENSION_GRANT, Acl.DELETE)
                .andThen(extensionGrantService.delete(domain, extensionGrant, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
