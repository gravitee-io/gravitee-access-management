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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.EntityStore;
import io.gravitee.am.model.EntityStoreVersion;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.EntityStoreService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.EntityStoreNotFoundException;
import io.gravitee.am.service.model.UpdateEntityStore;
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
import jakarta.validation.constraints.Min;
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
@Tag(name = "entity store")
public class EntityStoreResource extends AbstractResource {

    @Autowired
    private EntityStoreService entityStoreService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findEntityStore",
            summary = "Get an entity store",
            description = "User must have the DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity store",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EntityStore.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("entityStoreId") String entityStoreId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_BUNDLE, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> entityStoreService.findByDomainAndId(domainId, entityStoreId))
                        .switchIfEmpty(Maybe.error(new EntityStoreNotFoundException(entityStoreId))))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateEntityStore",
            summary = "Update an entity store",
            description = "User must have the DOMAIN_AUTHORIZATION_BUNDLE[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity store successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EntityStore.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("entityStoreId") String entityStoreId,
            @Parameter(name = "entityStore", required = true) @Valid @NotNull UpdateEntityStore updateEntityStore,
            @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_BUNDLE, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(existingDomain -> entityStoreService.update(existingDomain, entityStoreId, updateEntityStore, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteEntityStore",
            summary = "Delete an entity store",
            description = "User must have the DOMAIN_AUTHORIZATION_BUNDLE[DELETE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[DELETE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entity store successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("entityStoreId") String entityStoreId,
            @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_BUNDLE, Acl.DELETE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(existingDomain -> entityStoreService.delete(existingDomain, entityStoreId, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @GET
    @Path("versions")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listEntityStoreVersions",
            summary = "List all versions of an entity store",
            description = "User must have the DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of entity store versions",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = EntityStoreVersion.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void listVersions(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("entityStoreId") String entityStoreId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_BUNDLE, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> entityStoreService.findByDomainAndId(domainId, entityStoreId))
                        .switchIfEmpty(Maybe.error(new EntityStoreNotFoundException(entityStoreId)))
                        .flatMapPublisher(es -> entityStoreService.getVersions(entityStoreId))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("versions/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getEntityStoreVersion",
            summary = "Get a specific version of an entity store",
            description = "User must have the DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity store version",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EntityStoreVersion.class))),
            @ApiResponse(responseCode = "404", description = "Version not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getVersion(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("entityStoreId") String entityStoreId,
            @PathParam("version") @Min(1) int version,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_BUNDLE, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> entityStoreService.findByDomainAndId(domainId, entityStoreId))
                        .switchIfEmpty(Maybe.error(new EntityStoreNotFoundException(entityStoreId)))
                        .flatMap(es -> entityStoreService.getVersion(entityStoreId, version))
                        .switchIfEmpty(Maybe.error(new EntityStoreNotFoundException(entityStoreId))))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("versions/{version}/restore")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "restoreEntityStoreVersion",
            summary = "Restore an entity store to a specific version",
            description = "User must have the DOMAIN_AUTHORIZATION_BUNDLE[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity store restored to specified version",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EntityStore.class))),
            @ApiResponse(responseCode = "404", description = "Version not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void restoreVersion(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("entityStoreId") String entityStoreId,
            @PathParam("version") @Min(1) int version,
            @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_BUNDLE, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(existingDomain -> entityStoreService.restoreVersion(existingDomain, entityStoreId, version, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }
}
