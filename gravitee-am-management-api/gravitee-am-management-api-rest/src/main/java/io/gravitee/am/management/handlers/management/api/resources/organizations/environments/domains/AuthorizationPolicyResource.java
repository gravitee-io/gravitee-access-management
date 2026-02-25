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
import io.gravitee.am.model.AuthorizationPolicy;
import io.gravitee.am.model.AuthorizationPolicyVersion;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AuthorizationPolicyService;
import io.gravitee.am.service.exception.AuthorizationPolicyNotFoundException;
import io.gravitee.am.service.exception.AuthorizationPolicyVersionNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateAuthorizationPolicy;
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
@Tag(name = "authorization policy")
public class AuthorizationPolicyResource extends AbstractResource {

    @Autowired
    private AuthorizationPolicyService authorizationPolicyService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findAuthorizationPolicy",
            summary = "Get an authorization policy",
            description = "User must have the DOMAIN_AUTHORIZATION_POLICY[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_POLICY[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_POLICY[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization policy",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationPolicy.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("policyId") String policyId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_POLICY, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> authorizationPolicyService.findById(policyId))
                        .switchIfEmpty(Maybe.error(new AuthorizationPolicyNotFoundException(policyId))))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateAuthorizationPolicy",
            summary = "Update an authorization policy",
            description = "User must have the DOMAIN_AUTHORIZATION_POLICY[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_POLICY[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_POLICY[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization policy successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationPolicy.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("policyId") String policyId,
            @Parameter(name = "authorizationPolicy", required = true) @Valid @NotNull UpdateAuthorizationPolicy updateAuthorizationPolicy,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_POLICY, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(existingDomain -> authorizationPolicyService.update(existingDomain, policyId, updateAuthorizationPolicy, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteAuthorizationPolicy",
            summary = "Delete an authorization policy",
            description = "User must have the DOMAIN_AUTHORIZATION_POLICY[DELETE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_POLICY[DELETE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_POLICY[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Authorization policy successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("policyId") String policyId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_POLICY, Acl.DELETE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(existingDomain -> authorizationPolicyService.delete(existingDomain, policyId, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @GET
    @Path("versions")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listAuthorizationPolicyVersions",
            summary = "List version history for an authorization policy",
            description = "User must have the DOMAIN_AUTHORIZATION_POLICY[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_POLICY[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_POLICY[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization policy version history",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AuthorizationPolicyVersion.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getVersionHistory(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("policyId") String policyId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_POLICY, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapPublisher(__ -> authorizationPolicyService.getVersionHistory(policyId))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("versions/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findAuthorizationPolicyVersion",
            summary = "Get a specific version of an authorization policy",
            description = "User must have the DOMAIN_AUTHORIZATION_POLICY[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_POLICY[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_POLICY[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization policy version",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationPolicyVersion.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getVersion(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("policyId") String policyId,
            @PathParam("version") int version,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_POLICY, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> authorizationPolicyService.getVersion(policyId, version))
                        .switchIfEmpty(Maybe.error(new AuthorizationPolicyVersionNotFoundException(policyId, version))))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("rollback")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "rollbackAuthorizationPolicy",
            summary = "Rollback an authorization policy to a specific version",
            description = "User must have the DOMAIN_AUTHORIZATION_POLICY[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_POLICY[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_POLICY[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization policy successfully rolled back",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationPolicy.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void rollback(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("policyId") String policyId,
            @Parameter(name = "rollbackRequest", required = true) @Valid @NotNull RollbackRequest rollbackRequest,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_POLICY, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(existingDomain -> authorizationPolicyService.rollback(existingDomain, policyId, rollbackRequest.getVersion(), authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }
}
