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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.permissions.Permission;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Tag(name = "Domains")
public class DomainResource extends AbstractAutomationResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationGetDomain", summary = "Get a domain",
            description = "Retrieves a single security domain by its HRID.")
    @ApiResponse(responseCode = "200", description = "The domain",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Domain.class)))
    public void get(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainHrid") String domainHrid,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        domainService.findByHrid(environmentId, domainHrid)
                .flatMap(domain -> checkAnyPermission(principal, organizationId, environmentId, domain.getId(), Permission.DOMAIN, Acl.READ)
                        .andThen(Single.just(domain)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(operationId = "automationDeleteDomain", summary = "Delete a domain")
    @ApiResponse(responseCode = "204", description = "Domain deleted")
    public void delete(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainHrid") String domainHrid,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();

        domainService.findByHrid(environmentId, domainHrid)
                .flatMapCompletable(domain ->
                    checkAnyPermission(principal, organizationId, environmentId, domain.getId(), Permission.DOMAIN, Acl.DELETE)
                            .andThen(domainService.delete(new GraviteeContext(organizationId, environmentId, domain.getId()), domain.getId(), principal))
                )
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @Path("/identity-providers")
    public IdentityProvidersResource getIdentityProvidersResource() {
        return resourceContext.getResource(IdentityProvidersResource.class);
    }
}
