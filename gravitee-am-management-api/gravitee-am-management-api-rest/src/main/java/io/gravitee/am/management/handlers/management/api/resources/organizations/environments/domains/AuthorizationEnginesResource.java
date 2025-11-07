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
import io.gravitee.am.management.service.AuthorizationEngineServiceProxy;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewAuthorizationEngine;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * @author GraviteeSource Team
 */
@Tag(name = "authorization engine")
public class AuthorizationEnginesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private AuthorizationEngineServiceProxy authorizationEngineService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listAuthorizationEngines",
            summary = "List registered authorization engines for a security domain",
            description = "User must have the DOMAIN_AUTHORIZATION_ENGINE[LIST] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[LIST] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[LIST] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List registered authorization engines for a security domain",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AuthorizationEngine.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_ENGINE, Acl.LIST)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapPublisher(__ -> authorizationEngineService.findByDomain(domainId))
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createAuthorizationEngine",
            summary = "Create an authorization engine",
            description = "User must have the DOMAIN_AUTHORIZATION_ENGINE[CREATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[CREATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Authorization engine successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationEngine.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "authorizationEngine", required = true)
            @Valid @NotNull final NewAuthorizationEngine newAuthorizationEngine,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_ENGINE, Acl.CREATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(__ -> authorizationEngineService.create(domainId, newAuthorizationEngine, authenticatedUser))
                        .map(authorizationEngine -> Response.created(URI.create("/organizations/" + organizationId + "/environments/"
                                                    + environmentId + "/domains/" + domainId + "/authorization-engines/" + authorizationEngine.getId()))
                                            .entity(authorizationEngine)
                                            .build()
                        ))
                .subscribe(response::resume, response::resume);
    }

    @Path("{engineId}")
    public AuthorizationEngineResource getAuthorizationEngineResource() {
        return resourceContext.getResource(AuthorizationEngineResource.class);
    }
}
