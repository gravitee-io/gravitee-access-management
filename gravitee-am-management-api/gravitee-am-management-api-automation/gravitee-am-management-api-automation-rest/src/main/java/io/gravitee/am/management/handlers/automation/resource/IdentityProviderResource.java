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

import io.gravitee.am.management.handlers.automation.mapper.AutomationIdentityProviderMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationIdentityProvider;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.IdentityProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A single identity provider managed under a domain, addressed by its key.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Identity Providers")
public class IdentityProviderResource extends AbstractAutomationResource {

    @Autowired
    private IdentityProviderService identityProviderService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationGetIdentityProvider", summary = "Get an identity provider",
            description = "Retrieves a single Automation-managed identity provider by its key.")
    @ApiResponse(responseCode = "200", description = "The identity provider",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationIdentityProvider.class)))
    @ApiResponse(responseCode = "404", description = "Domain or identity provider not found, or not managed by the Automation API")
    public void get(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("identityKey") String identityKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(domainKey);
        final AutomationRef identityRef = AutomationRef.parse(identityKey);
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.READ)
                .andThen(resolver.resolveDomain(environmentId, domainRef))
                .flatMap(domain -> resolver.resolveIdentityProvider(domain, identityRef)
                        .map(AutomationIdentityProviderMapper::toAutomationIdentityProvider))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(operationId = "automationDeleteIdentityProvider", summary = "Delete an identity provider",
            description = "Deletes an Automation-managed identity provider by its key. Deleting an identity " +
                    "provider that does not exist also returns 204.")
    @ApiResponse(responseCode = "204", description = "Identity provider successfully deleted")
    public void delete(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("identityKey") String identityKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(domainKey);
        final AutomationRef identityRef = AutomationRef.parse(identityKey);
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.DELETE)
                .andThen(resolver.resolveDomainMaybe(environmentId, domainRef))
                .flatMapCompletable(domain -> resolver.resolveIdentityProviderMaybe(domain, identityRef)
                        .flatMapCompletable(idp -> identityProviderService.delete(ReferenceType.DOMAIN, domain.getId(), idp.getId(), principal)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
