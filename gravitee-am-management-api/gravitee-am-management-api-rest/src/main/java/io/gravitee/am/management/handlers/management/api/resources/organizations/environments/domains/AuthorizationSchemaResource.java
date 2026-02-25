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
import io.gravitee.am.management.handlers.management.api.model.RollbackRequest;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.AuthorizationSchema;
import io.gravitee.am.model.AuthorizationSchemaVersion;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AuthorizationSchemaService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateAuthorizationSchema;
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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
@Tag(name = "authorization schema")
public class AuthorizationSchemaResource extends AbstractResource {

    @Autowired
    private AuthorizationSchemaService authorizationSchemaService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findAuthorizationSchema",
            summary = "Get authorization schema for a security domain",
            description = "User must have the DOMAIN_AUTHORIZATION_SCHEMA[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization schema",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationSchema.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_SCHEMA, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> authorizationSchemaService.findByDomain(domainId)))
                .subscribe(
                        response::resume,
                        response::resume,
                        () -> response.resume(Response.noContent().build()));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createOrUpdateAuthorizationSchema",
            summary = "Create or update authorization schema for a security domain",
            description = "User must have the DOMAIN_AUTHORIZATION_SCHEMA[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization schema successfully created or updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationSchema.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void createOrUpdate(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "authorizationSchema", required = true) @Valid @NotNull UpdateAuthorizationSchema updateAuthorizationSchema,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_SCHEMA, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(existingDomain -> authorizationSchemaService.createOrUpdate(existingDomain, updateAuthorizationSchema, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteAuthorizationSchema",
            summary = "Delete authorization schema for a security domain",
            description = "User must have the DOMAIN_AUTHORIZATION_SCHEMA[DELETE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[DELETE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Authorization schema successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_SCHEMA, Acl.DELETE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(existingDomain -> authorizationSchemaService.delete(existingDomain, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @GET
    @Path("versions")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listAuthorizationSchemaVersions",
            summary = "List version history for authorization schema",
            description = "User must have the DOMAIN_AUTHORIZATION_SCHEMA[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization schema version history",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AuthorizationSchemaVersion.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getVersionHistory(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_SCHEMA, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapPublisher(__ -> authorizationSchemaService.getVersionHistory(domainId))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("rollback")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "rollbackAuthorizationSchema",
            summary = "Rollback authorization schema to a specific version",
            description = "User must have the DOMAIN_AUTHORIZATION_SCHEMA[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_SCHEMA[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization schema successfully rolled back",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationSchema.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void rollback(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "rollbackRequest", required = true) @Valid @NotNull RollbackRequest rollbackRequest,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_SCHEMA, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(existingDomain -> authorizationSchemaService.rollback(existingDomain, rollbackRequest.getVersion(), authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }
}
