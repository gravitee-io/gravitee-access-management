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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.AuthorizationBundle;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AuthorizationBundleService;
import io.gravitee.am.service.exception.AuthorizationBundleNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateAuthorizationBundle;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author GraviteeSource Team
 */
@Tag(name = "authorization bundle")
public class AuthorizationBundleResource extends AbstractResource {

    @Autowired
    private AuthorizationBundleService authorizationBundleService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findAuthorizationBundle",
            summary = "Get an authorization bundle",
            description = "User must have the DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization bundle",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationBundle.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("bundleId") String bundleId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_BUNDLE, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> authorizationBundleService.findByDomainAndId(domainId, bundleId))
                        .switchIfEmpty(Maybe.error(new AuthorizationBundleNotFoundException(bundleId))))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateAuthorizationBundle",
            summary = "Update an authorization bundle",
            description = "User must have the DOMAIN_AUTHORIZATION_BUNDLE[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization bundle successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationBundle.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("bundleId") String bundleId,
            @Parameter(name = "authorizationBundle", required = true) @Valid @NotNull UpdateAuthorizationBundle updateAuthorizationBundle,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_BUNDLE, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(existingDomain -> authorizationBundleService.update(existingDomain, bundleId, updateAuthorizationBundle, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteAuthorizationBundle",
            summary = "Delete an authorization bundle",
            description = "User must have the DOMAIN_AUTHORIZATION_BUNDLE[DELETE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[DELETE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_BUNDLE[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Authorization bundle successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("bundleId") String bundleId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_BUNDLE, Acl.DELETE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(existingDomain -> authorizationBundleService.delete(existingDomain, bundleId, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
