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

import io.gravitee.am.management.handlers.management.api.model.ScopeApprovalEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listUserConsents",
            deprecated = true,
            summary = "Get a user consents",
            description = "User must have the DOMAIN_USER[READ] permission on the specified domain " +
                    "or DOMAIN_USER[READ] permission on the specified environment " +
                    "or DOMAIN_USER[READ] permission on the specified organization. " +
                    "Deprecated: use GET /consents/scopes instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User consents successfully fetched",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ScopeApprovalEntity.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    @Deprecated
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @QueryParam("clientId") String clientId,
            @Suspended final AsyncResponse response) {
        resourceContext.getResource(UserScopeConsentsResource.class).list(organizationId, environmentId, domainId, user, clientId, response);
    }

    @DELETE
    @Operation(
            operationId = "revokeUserConsents",
            deprecated = true,
            summary = "Revoke user consents",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization. " +
                    "Deprecated: use DELETE /consents/scopes instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User consents successfully revoked"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    @Deprecated
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @QueryParam("clientId") String clientId,
            @Suspended final AsyncResponse response) {
        resourceContext.getResource(UserScopeConsentsResource.class).delete(organizationId, environmentId, domainId, user, clientId, response);
    }

    @Path("scopes")
    public UserScopeConsentsResource getUserScopeConsentsResource() {
        return resourceContext.getResource(UserScopeConsentsResource.class);
    }

    @Path("authorization-details")
    public UserAuthorizationDetailConsentsResource getUserAuthorizationDetailConsentsResource() {
        return resourceContext.getResource(UserAuthorizationDetailConsentsResource.class);
    }

    @Path("{consent}")
    public UserConsentResource getUserConsentResource() {
        return resourceContext.getResource(UserConsentResource.class);
    }
}
