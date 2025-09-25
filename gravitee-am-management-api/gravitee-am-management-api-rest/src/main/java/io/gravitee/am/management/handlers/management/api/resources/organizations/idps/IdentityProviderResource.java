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
package io.gravitee.am.management.handlers.management.api.resources.organizations.idps;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.am.service.validators.idp.DatasourceValidator;
import io.gravitee.common.http.MediaType;
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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private IdentityProviderServiceProxy identityProviderService;

    @Autowired
    private DatasourceValidator datasourceValidator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get an identity provider",
            description = "User must have the ORGANIZATION_IDENTITY_PROVIDER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identity provider",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = IdentityProvider.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("identity") String identityProvider,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_IDENTITY_PROVIDER, Acl.READ)
                .andThen(identityProviderService.findById(ReferenceType.ORGANIZATION, organizationId, identityProvider))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update an identity provider",
            description = "User must have the ORGANIZATION_IDENTITY_PROVIDER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Identity provider successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = IdentityProvider.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("identity") String identity,
            @Parameter(name = "identity", required = true) @Valid @NotNull UpdateIdentityProvider updateIdentityProvider,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_IDENTITY_PROVIDER, Acl.UPDATE)
                .andThen(datasourceValidator.validate(updateIdentityProvider.getConfiguration()))
                .andThen(identityProviderService.update(ReferenceType.ORGANIZATION, organizationId, identity, updateIdentityProvider, authenticatedUser, false))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(summary = "Delete an identity provider",
            description = "User must have the ORGANIZATION_IDENTITY_PROVIDER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Identity provider successfully deleted"),
            @ApiResponse(responseCode = "400", description = "Identity provider is bind to existing clients"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("identity") String identity,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_IDENTITY_PROVIDER, Acl.DELETE)
                .andThen(identityProviderService.delete(ReferenceType.ORGANIZATION, organizationId, identity, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
