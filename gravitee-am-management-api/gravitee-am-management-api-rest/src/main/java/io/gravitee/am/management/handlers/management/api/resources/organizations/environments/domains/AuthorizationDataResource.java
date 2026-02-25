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
import io.gravitee.am.model.AuthorizationData;
import io.gravitee.am.model.AuthorizationDataVersion;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AuthorizationDataService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateAuthorizationData;
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
@Tag(name = "authorization data")
public class AuthorizationDataResource extends AbstractResource {

    @Autowired
    private AuthorizationDataService authorizationDataService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findAuthorizationData",
            summary = "Get authorization data for a security domain",
            description = "User must have the DOMAIN_AUTHORIZATION_DATA[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_DATA[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_DATA[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization data",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationData.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_DATA, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> authorizationDataService.findByDomain(domainId)))
                .subscribe(
                        response::resume,
                        response::resume,
                        () -> response.resume(Response.noContent().build()));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createOrUpdateAuthorizationData",
            summary = "Create or update authorization data for a security domain",
            description = "User must have the DOMAIN_AUTHORIZATION_DATA[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_DATA[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_DATA[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization data successfully created or updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationData.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void createOrUpdate(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "authorizationData", required = true) @Valid @NotNull UpdateAuthorizationData updateAuthorizationData,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_DATA, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(existingDomain -> authorizationDataService.createOrUpdate(existingDomain, updateAuthorizationData, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteAuthorizationData",
            summary = "Delete authorization data for a security domain",
            description = "User must have the DOMAIN_AUTHORIZATION_DATA[DELETE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_DATA[DELETE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_DATA[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Authorization data successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_DATA, Acl.DELETE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(existingDomain -> authorizationDataService.delete(existingDomain, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @GET
    @Path("versions")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listAuthorizationDataVersions",
            summary = "List version history for authorization data",
            description = "User must have the DOMAIN_AUTHORIZATION_DATA[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_DATA[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_DATA[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization data version history",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AuthorizationDataVersion.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getVersionHistory(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_DATA, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapPublisher(__ -> authorizationDataService.getVersionHistory(domainId))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("rollback")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "rollbackAuthorizationData",
            summary = "Rollback authorization data to a specific version",
            description = "User must have the DOMAIN_AUTHORIZATION_DATA[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_DATA[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_DATA[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization data successfully rolled back",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationData.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void rollback(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "rollbackRequest", required = true) @Valid @NotNull RollbackRequest rollbackRequest,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_DATA, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(existingDomain -> authorizationDataService.rollback(existingDomain, rollbackRequest.getVersion(), authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }
}
