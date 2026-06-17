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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.automation.mapper.AutomationDomainMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationDomain;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.AutomationNewDomain;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

import java.util.List;

/**
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Tag(name = "Domains")
public class DomainsResource extends AbstractAutomationResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationListDomains", summary = "List all domains",
            description = "Returns all security domains within the specified environment.")
    @ApiResponse(responseCode = "200", description = "List of domains",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AutomationDomain.class))))
    public void list(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN, Acl.LIST)
                .andThen(domainService.findAllByEnvironment(organizationId, environmentId)
                        .filter(domain -> domain.isManagedBy(ManagedBy.AUTOMATION_API))
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                        .map(AutomationDomainMapper::toAutomationDomain)
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationCreateOrUpdateDomain",
            summary = "Create or update a domain",
            description = "Idempotent create-or-update. Uses the key field in the body to identify the domain. " +
                    "On first apply the domain is created; subsequent applies update it. dataPlaneId is required " +
                    "at creation and immutable afterwards.")
    @ApiResponse(responseCode = "200", description = "The created or updated domain",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationDomain.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request: validation failure, an immutable field " +
            "change, a key that already exists for a domain not managed by the Automation API, or an unknown " +
            "defaultIdentityProviderForRegistration reference")
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Desired state of the domain.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutomationDomain.class),
                            examples = @ExampleObject(name = "Domain",
                                    description = "A domain with common settings",
                                    value = "{\"key\":\"example-domain\",\"name\":\"Example domain\"," +
                                            "\"description\":\"An example authentication domain\"," +
                                            "\"enabled\":true,\"path\":\"/example-domain\",\"dataPlaneId\":\"default\"," +
                                            "\"tags\":[\"eu\",\"production\"]," +
                                            "\"accountSettings\":{\"inherited\":false,\"loginAttemptsDetectionEnabled\":true,\"maxLoginAttempts\":10,\"accountBlockedDuration\":7200}," +
                                            "\"oidc\":{\"redirectUriStrictMatching\":true}}")))
            @Valid @NotNull AutomationDomain definition,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(definition.getAutomationKey());

        // An 'id:' body addresses a preexisting domain directly (update-only)
        if (domainRef instanceof AutomationRef.IdRef(String id)) {
            resolver.resolveDomain(environmentId, domainRef)
                    .flatMap(domain -> checkAnyPermission(principal, organizationId, environmentId, domain.getId(), Permission.DOMAIN, Acl.UPDATE)
                            .andThen(applyAndRespond(domain, definition, principal)))
                    .subscribe(response::resume, response::resume);
            return;
        }

        final String key = domainRef.raw();
        final String domainId = AutomationIds.domainId(environmentId, key);

        Single.defer(() ->
                domainService.findById(domainId)
                        .flatMap(existing ->
                                // Permission is checked before the non-automation guard so the existence of a
                                // domain the caller may not touch is not revealed ahead of authorization.
                                checkAnyPermission(principal, organizationId, environmentId, existing.getId(), Permission.DOMAIN, Acl.UPDATE)
                                        .andThen(existing.isManagedBy(ManagedBy.AUTOMATION_API)
                                                ? Maybe.just(existing)
                                                : Maybe.<Domain>error(new InvalidParameterException(
                                                        "Domain with key '" + key + "' already exists in this environment and is not managed by the Automation API"))))
                        .switchIfEmpty(Single.defer(() ->
                                checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN, Acl.CREATE)
                                        .andThen(Single.defer(() -> {
                                            AutomationNewDomain newDomain = new AutomationNewDomain();
                                            newDomain.setId(domainId);
                                            newDomain.setAutomationKey(key);
                                            newDomain.setName(definition.getName());
                                            newDomain.setPath(definition.getPath());
                                            newDomain.setDescription(definition.getDescription());
                                            newDomain.setDataPlaneId(definition.getDataPlaneId());
                                            return domainService.create(organizationId, environmentId, newDomain, principal);
                                        }))))
                        .flatMap(domain -> applyAndRespond(domain, definition, principal)))
                .subscribe(response::resume, response::resume);
    }

    @Path("/{domainKey}")
    public DomainResource getDomainResource() {
        return resourceContext.getResource(DomainResource.class);
    }

    /**
     * Resolves the {@code accountSettings.defaultIdentityProviderForRegistration} key reference against
     * the domain's existing identity providers, applies the full domain settings, then persists with a
     * reference-lenient update — certificate / identity-provider references may point to resources that
     * do not exist yet (eventual consistency), so they are not validated here.
     */
    private Single<AutomationDomain> applyAndRespond(Domain domain, AutomationDomain definition, User principal) {
        return automationIdentityProviders(domain.getId())
                .flatMap(idps -> {
                    AutomationDomainMapper.applyTo(definition, domain, idps);
                    return domainService.update(domain.getId(), domain, false);
                })
                .map(AutomationDomainMapper::toAutomationDomain);
    }

    private Single<List<IdentityProvider>> automationIdentityProviders(String domainId) {
        return identityProviderService.findAll(ReferenceType.DOMAIN, domainId)
                .filter(idp -> idp.isManagedBy(ManagedBy.AUTOMATION_API))
                .toList();
    }
}
