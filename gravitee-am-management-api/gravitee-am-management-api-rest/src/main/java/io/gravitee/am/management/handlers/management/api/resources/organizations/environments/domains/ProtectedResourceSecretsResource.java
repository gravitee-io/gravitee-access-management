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
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;
import io.gravitee.am.service.model.NewClientSecret;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Collectors;

import static io.gravitee.am.model.Acl.CREATE;
import static io.gravitee.am.model.Acl.LIST;
import static io.gravitee.am.model.Acl.DELETE;
import static io.gravitee.am.model.Acl.UPDATE;

public class ProtectedResourceSecretsResource extends AbstractResource {

    @Autowired
    private ProtectedResourceService protectedResourceService;

    @Autowired
    private DomainService domainService;
    
    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List secrets of a protected resource",
               description = "User must have the PROTECTED_RESOURCE[LIST] permission on the specified resource " +
                    "or PROTECTED_RESOURCE[LIST] permission on the specified domain " +
                    "or PROTECTED_RESOURCE[LIST] permission on the specified environment " +
                    "or PROTECTED_RESOURCE[LIST] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List secrets of a protected resource",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ClientSecret.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getSecrets(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("protected-resource") String protectedResource,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, ReferenceType.PROTECTED_RESOURCE, protectedResource, Permission.PROTECTED_RESOURCE, LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> protectedResourceService.findById(protectedResource))
                        .switchIfEmpty(Single.error(new ProtectedResourceNotFoundException(protectedResource)))
                        .map(resource -> resource.getClientSecrets() != null ? 
                            resource.getClientSecrets().stream().map(ClientSecret::safeSecret).collect(Collectors.toList()) : 
                            java.util.Collections.emptyList())) 
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a secret for a protected resource",
               description = "User must have the PROTECTED_RESOURCE[CREATE] permission on the specified resource " +
                    "or PROTECTED_RESOURCE[CREATE] permission on the specified domain " +
                    "or PROTECTED_RESOURCE[CREATE] permission on the specified environment " +
                    "or PROTECTED_RESOURCE[CREATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Protected Resource secret successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ClientSecret.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("protected-resource") String protectedResource,
            @Valid @NotNull final NewClientSecret clientSecret,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, ReferenceType.PROTECTED_RESOURCE, protectedResource, Permission.PROTECTED_RESOURCE, CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(existingDomain -> protectedResourceService.createSecret(existingDomain, protectedResource, clientSecret.getName(), authenticatedUser))
                        .map(secret -> Response.status(Response.Status.CREATED).entity(secret).build()))
                .subscribe(response::resume, response::resume);
    }
    
    @POST
    @Path("{secretId}/_renew")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Renew a secret for a protected resource",
               description = "User must have the PROTECTED_RESOURCE[UPDATE] permission on the specified resource " +
                    "or PROTECTED_RESOURCE[UPDATE] permission on the specified domain " +
                    "or PROTECTED_RESOURCE[UPDATE] permission on the specified environment " +
                    "or PROTECTED_RESOURCE[UPDATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Protected Resource secret successfully renewed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ClientSecret.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void renew(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("protected-resource") String protectedResource,
            @PathParam("secretId") String secretId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, ReferenceType.PROTECTED_RESOURCE, protectedResource, Permission.PROTECTED_RESOURCE, UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(existingDomain -> protectedResourceService.renewSecret(existingDomain, protectedResource, secretId, authenticatedUser))
                        .map(secret -> Response.status(Response.Status.OK).entity(secret).build()))
                .subscribe(response::resume, response::resume);
    }
    
    @DELETE
    @Path("{secretId}")
    @Operation(summary = "Remove a secret for a protected resource",
               description = "User must have the PROTECTED_RESOURCE[DELETE] permission on the specified resource " +
                    "or PROTECTED_RESOURCE[DELETE] permission on the specified domain " +
                    "or PROTECTED_RESOURCE[DELETE] permission on the specified environment " +
                    "or PROTECTED_RESOURCE[DELETE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Protected Resource secret successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Protected Resource or secret not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("protected-resource") String protectedResource,
            @PathParam("secretId") String secretId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, ReferenceType.PROTECTED_RESOURCE, protectedResource, Permission.PROTECTED_RESOURCE, DELETE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapCompletable(existingDomain -> protectedResourceService.deleteSecret(existingDomain, protectedResource, secretId, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
