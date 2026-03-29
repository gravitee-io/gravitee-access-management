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

import io.gravitee.am.management.handlers.automation.model.AutomationIdentityProviderDefinition;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.IdentityProvider;

import java.util.UUID;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Tag(name = "Identity Providers")
public class IdentityProvidersResource extends AbstractAutomationResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listIdentityProviders", summary = "List all identity providers",
            description = "Returns all identity providers within the specified domain.")
    @ApiResponse(responseCode = "200", description = "List of identity providers",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = IdentityProvider.class))))
    public void list(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainHrid") String domainHrid,
            @Suspended final AsyncResponse response) {

        domainService.findByHrid(environmentId, domainHrid)
                .flatMapPublisher(domain -> identityProviderService.findAll(ReferenceType.DOMAIN, domain.getId()))
                .toList()
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createOrUpdateIdentityProvider",
            summary = "Create or update an identity provider",
            description = "Idempotent create-or-update. Uses the hrid field in the body to identify the identity provider.")
    @ApiResponse(responseCode = "200", description = "The created or updated identity provider",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = IdentityProvider.class)))
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainHrid") String domainHrid,
            @Valid @NotNull AutomationIdentityProviderDefinition definition,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final String idpHrid = definition.getHrid();

        domainService.findByHrid(environmentId, domainHrid)
                .flatMap(domain -> {
                    String idpId = deterministicId(domain.getId(), idpHrid);
                    return identityProviderService.findById(ReferenceType.DOMAIN, domain.getId(), idpId)
                            .flatMap(existingIdp -> {
                                UpdateIdentityProvider update = toUpdateIdentityProvider(definition);
                                return identityProviderService.update(ReferenceType.DOMAIN, domain.getId(), existingIdp.getId(), update, principal, false);
                            })
                            .onErrorResumeNext(throwable -> {
                                NewIdentityProvider newIdp = toNewIdentityProvider(definition);
                                newIdp.setId(idpId);
                                return identityProviderService.create(domain, newIdp, principal);
                            });
                })
                .subscribe(response::resume, response::resume);
    }

    @Path("/{idpId}")
    public IdentityProviderResource getIdentityProviderResource() {
        return resourceContext.getResource(IdentityProviderResource.class);
    }

    private NewIdentityProvider toNewIdentityProvider(AutomationIdentityProviderDefinition definition) {
        NewIdentityProvider newIdp = new NewIdentityProvider();
        newIdp.setName(definition.getName());
        newIdp.setType(definition.getType());
        newIdp.setConfiguration(definition.getConfiguration());
        newIdp.setDomainWhitelist(definition.getDomainWhitelist());
        return newIdp;
    }

    private UpdateIdentityProvider toUpdateIdentityProvider(AutomationIdentityProviderDefinition definition) {
        UpdateIdentityProvider update = new UpdateIdentityProvider();
        update.setName(definition.getName());
        update.setType(definition.getType());
        update.setConfiguration(definition.getConfiguration());
        update.setMappers(definition.getMappers());
        update.setRoleMapper(definition.getRoleMapper());
        update.setGroupMapper(definition.getGroupMapper());
        update.setDomainWhitelist(definition.getDomainWhitelist());
        return update;
    }

    private static String deterministicId(String domainId, String hrid) {
        return UUID.nameUUIDFromBytes((domainId + "/" + hrid).getBytes()).toString();
    }
}
