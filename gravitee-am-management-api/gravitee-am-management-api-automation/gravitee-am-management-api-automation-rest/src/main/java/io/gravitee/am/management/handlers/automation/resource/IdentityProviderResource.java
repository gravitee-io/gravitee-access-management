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
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
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
    private DomainService domainService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationGetIdentityProvider", summary = "Get an identity provider",
            description = "Retrieves a single identity provider by its key.")
    @ApiResponse(responseCode = "200", description = "The identity provider",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationIdentityProvider.class)))
    public void get(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("idpKey") String idpKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.READ)
                .andThen(resolveDomain(environmentId, domainKey))
                .flatMap(domain -> resolveIdentityProvider(domain, idpKey)
                        .map(AutomationIdentityProviderMapper::toAutomationIdentityProvider))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(operationId = "automationDeleteIdentityProvider", summary = "Delete an identity provider")
    @ApiResponse(responseCode = "204", description = "Identity provider deleted")
    public void delete(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("idpKey") String idpKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.DELETE)
                .andThen(resolveDomainMaybe(environmentId, domainKey))
                .flatMapCompletable(domain -> identityProviderService.findAll(ReferenceType.DOMAIN, domain.getId())
                        .filter(idp -> idp.isManagedBy(ManagedBy.AUTOMATION_API) && idpKey.equals(idp.getAutomationKey()))
                        .firstElement()
                        .flatMapCompletable(idp -> identityProviderService.delete(ReferenceType.DOMAIN, domain.getId(), idp.getId(), principal)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    private Single<Domain> resolveDomain(String environmentId, String domainKey) {
        return domainService.findById(AutomationIds.domainId(environmentId, domainKey))
                .switchIfEmpty(Single.error(() -> new DomainNotFoundException(domainKey)))
                .flatMap(domain -> domain.isManagedBy(ManagedBy.AUTOMATION_API)
                        ? Single.just(domain)
                        : Single.error(new DomainNotFoundException(domainKey)));
    }

    private Maybe<Domain> resolveDomainMaybe(String environmentId, String domainKey) {
        return domainService.findById(AutomationIds.domainId(environmentId, domainKey))
                .filter(domain -> domain.isManagedBy(ManagedBy.AUTOMATION_API));
    }

    /**
     * Resolves an automation-managed identity provider by its {@code key} (not its internal id) so that a
     * system provider — which adopts the conventional {@code default-idp-<domainId>} id rather than the
     * deterministic key-based id — resolves like any other.
     */
    private Single<IdentityProvider> resolveIdentityProvider(Domain domain, String idpKey) {
        return identityProviderService.findAll(ReferenceType.DOMAIN, domain.getId())
                .filter(idp -> idp.isManagedBy(ManagedBy.AUTOMATION_API) && idpKey.equals(idp.getAutomationKey()))
                .firstElement()
                .switchIfEmpty(Maybe.error(() -> new IdentityProviderNotFoundException(idpKey)))
                .toSingle();
    }
}
