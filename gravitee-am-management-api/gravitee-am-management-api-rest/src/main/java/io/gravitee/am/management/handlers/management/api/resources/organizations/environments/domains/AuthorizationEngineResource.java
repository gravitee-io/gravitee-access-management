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
import io.gravitee.am.management.service.AuthorizationEngineManager;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AuthorizationEngineService;
import io.gravitee.am.service.exception.AuthorizationEngineNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateAuthorizationEngine;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author GraviteeSource Team
 */
public class AuthorizationEngineResource extends AbstractResource {

    @Autowired
    private AuthorizationEngineService authorizationEngineService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private AuthorizationEngineManager authorizationEngineManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findAuthorizationEngine",
            summary = "Get an authorization engine",
            description = "User must have the DOMAIN_AUTHORIZATION_ENGINE[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization engine",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationEngine.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("engineId") String engineId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_ENGINE, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(irrelevant -> authorizationEngineService.findById(engineId))
                        .switchIfEmpty(Maybe.error(new AuthorizationEngineNotFoundException(engineId)))
                        .map(authorizationEngine -> Response.ok(authorizationEngine).build()))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateAuthorizationEngine",
            summary = "Update an authorization engine",
            description = "User must have the DOMAIN_AUTHORIZATION_ENGINE[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization engine successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationEngine.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("engineId") String engineId,
            @Parameter(name = "authorizationEngine", required = true) @Valid @NotNull UpdateAuthorizationEngine updateAuthorizationEngine,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_ENGINE, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(__ -> authorizationEngineService.update(domainId, engineId, updateAuthorizationEngine, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteAuthorizationEngine",
            summary = "Delete an authorization engine",
            description = "User must have the DOMAIN_AUTHORIZATION_ENGINE[DELETE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[DELETE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_ENGINE[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Authorization engine successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("engineId") String engineId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_ENGINE, Acl.DELETE)
                .andThen(authorizationEngineService.delete(domainId, engineId, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    /**
     * Sub-resource locator for plugin-specific management endpoints.
     *
     * Note: Sub-resource locators must return synchronously as JAX-RS needs the resource instance
     * to build routing. Used blockingGet() here but wrap in a reactive permission check.
     */
    @Path("settings")
    public Object getPluginManagementResource(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("engineId") String engineId) {

        // Check permission and get resource in a single reactive chain
        return checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_ENGINE, Acl.UPDATE)
                .andThen(authorizationEngineManager.getProvider(engineId)
                        .flatMap(provider -> Maybe.fromOptional(provider.getManagementResource())))
                .blockingGet();
    }
}
