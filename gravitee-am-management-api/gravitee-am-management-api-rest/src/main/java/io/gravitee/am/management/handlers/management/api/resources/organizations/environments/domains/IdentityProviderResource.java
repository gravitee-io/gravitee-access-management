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
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.model.AssignPasswordPolicy;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.am.service.validators.idp.DatasourceValidator;
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
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private IdentityProviderServiceProxy identityProviderService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private DatasourceValidator datasourceValidator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findIdentityProvider",
            summary = "Get an identity provider",
            description = "User must have the DOMAIN_IDENTITY_PROVIDER[READ] permission on the specified domain " +
                    "or DOMAIN_IDENTITY_PROVIDER[READ] permission on the specified environment " +
                    "or DOMAIN_IDENTITY_PROVIDER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identity provider",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = IdentityProvider.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("identity") String identityProvider,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> identityProviderService.findById(identityProvider))
                        .switchIfEmpty(Maybe.error(new IdentityProviderNotFoundException(identityProvider)))
                        .map(this::hideConfiguration)
                        .map(safeIdp -> {
                            if (safeIdp.getReferenceType() == ReferenceType.DOMAIN
                                    && !safeIdp.getReferenceId().equalsIgnoreCase(domain)) {
                                throw new BadRequestException("Identity provider does not belong to domain");
                            }
                            return Response.ok(safeIdp).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    private IdentityProvider hideConfiguration(IdentityProvider identityProvider) {
        if (identityProvider.isSystem()) {
            identityProvider.setConfiguration(null);
        }
        return identityProvider;
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateIdentityProvider",
            summary = "Update an identity provider",
            description = "User must have the DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Identity provider successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = IdentityProvider.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("identity") String identity,
            @Parameter(name = "identity", required = true) @Valid @NotNull UpdateIdentityProvider updateIdentityProvider,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.UPDATE)
                .andThen(datasourceValidator.validate(updateIdentityProvider.getConfiguration()))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> identityProviderService.update(domain, identity, updateIdentityProvider, authenticatedUser, false)))
                .map(this::hideConfiguration)
                .subscribe(response::resume, response::resume);
    }


    @PUT
    @Path("/password-policy")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "assignPasswordPolicyToIdp", summary = "Assign password policy to identity provider",
            description = "User must have the DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_IDENTITY_PROVIDER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Password Policy successfully assigned to  Identity provider",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AssignPasswordPolicy.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void updatePasswordPolicy(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("identity") String identity,
            @Parameter(name = "passwordPolicy", required = true) @Valid @NotNull AssignPasswordPolicy assignPasswordPolicy,
            @Suspended final AsyncResponse response) {
        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.UPDATE)

                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> identityProviderService.findById(identity))
                        .flatMap(identityProvider ->
                            // only IDP with a UserProvider can contain password policy assigned
                            identityProviderManager.getUserProvider(identityProvider.getId())
                                    .switchIfEmpty(Maybe.error(new IdentityProviderNotFoundException(identity)))
                        )
                        .flatMapSingle(__ -> identityProviderService.updatePasswordPolicy(domain, identity, assignPasswordPolicy)))
                .map(this::hideConfiguration)
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteIdentityProvider",
            summary = "Delete an identity provider",
            description = "User must have the DOMAIN_IDENTITY_PROVIDER[DELETE] permission on the specified domain " +
                    "or DOMAIN_IDENTITY_PROVIDER[DELETE] permission on the specified environment " +
                    "or DOMAIN_IDENTITY_PROVIDER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Identity provider successfully deleted"),
            @ApiResponse(responseCode = "400", description = "Identity provider is bind to existing clients"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("identity") String identity,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.DELETE)
                .andThen(identityProviderService.delete(domain, identity, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
