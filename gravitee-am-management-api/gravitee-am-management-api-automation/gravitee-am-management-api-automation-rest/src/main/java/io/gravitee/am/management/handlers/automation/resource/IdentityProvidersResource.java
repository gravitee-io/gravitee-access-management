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
import io.gravitee.am.management.handlers.automation.mapper.AutomationIdentityProviderMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationIdentityProvider;
import io.gravitee.am.management.service.DefaultIdentityProviderService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.AutomationNewIdentityProvider;
import io.reactivex.rxjava3.core.Completable;
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
import java.util.Optional;

/**
 * Identity providers managed individually under a domain.
 *
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

    @Autowired
    private DefaultIdentityProviderService defaultIdentityProviderService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private PluginConfigurationValidationService validationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationListIdentityProviders", summary = "List a domain's identity providers",
            description = "Returns all identity providers managed by the Automation API under the domain. Identity " +
                    "providers created outside the Automation API are not returned.")
    @ApiResponse(responseCode = "200", description = "List of identity providers",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AutomationIdentityProvider.class))))
    @ApiResponse(responseCode = "404", description = "Domain not found, or not managed by the Automation API")
    public void list(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.LIST)
                .andThen(resolveDomain(environmentId, domainKey))
                .flatMap(domain -> identityProviderService.findAll(ReferenceType.DOMAIN, domain.getId())
                        .filter(idp -> idp.isManagedBy(ManagedBy.AUTOMATION_API))
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(
                                nullToEmpty(o1.getAutomationKey()), nullToEmpty(o2.getAutomationKey())))
                        .map(AutomationIdentityProviderMapper::toAutomationIdentityProvider)
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationCreateOrUpdateIdentityProvider",
            summary = "Create or update an identity provider",
            description = "Idempotent create-or-update. Uses the key field in the body to identify the identity " +
                    "provider within the domain. On first apply the identity provider is created; subsequent " +
                    "applies update it. The system flag is immutable; changing it requires deleting and recreating " +
                    "the identity provider.")
    @ApiResponse(responseCode = "200", description = "The created or updated identity provider",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationIdentityProvider.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request: a key conflict, a missing required field " +
            "(name or type) for a non-system identity provider, an attempt to change the immutable system flag, " +
            "or a second system identity provider for the domain")
    @ApiResponse(responseCode = "404", description = "Domain not found, or not managed by the Automation API")
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Desired state of the identity provider. For a system identity provider, supply " +
                            "only system: true and key.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutomationIdentityProvider.class),
                            examples = {
                                    @ExampleObject(name = "Identity provider", description = "A fully specified identity provider",
                                            value = "{\"key\":\"corporate-ldap\",\"name\":\"Corporate LDAP\"," +
                                                    "\"type\":\"inline-am-idp\"," +
                                                    "\"configuration\":\"{\\\"users\\\":[{\\\"username\\\":\\\"admin\\\",\\\"password\\\":\\\"...\\\"}]}\"," +
                                                    "\"mappers\":{\"sub\":\"username\",\"email\":\"email\"}," +
                                                    "\"roleMapper\":{\"role-id\":[\"username=admin\"]}," +
                                                    "\"groupMapper\":{\"group-id\":[\"username=admin\"]}," +
                                                    "\"domainWhitelist\":[\"example.com\"]}"),
                                    @ExampleObject(name = "System identity provider", description = "The domain's system identity provider; only system and key are needed",
                                            value = "{\"key\":\"default\",\"system\":true}")
                            }))
            @Valid @NotNull AutomationIdentityProvider definition,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final String domainId = AutomationIds.domainId(environmentId, domainKey);
        final String key = definition.getAutomationKey();
        identityProviderService.findAll(ReferenceType.DOMAIN, domainId).toList().flatMap(allExisting -> {
            // A resource is addressed by its key; a system IDP adopts the conventional default-idp id.
            Optional<IdentityProvider> match = allExisting.stream()
                    .filter(idp -> idp.isManagedBy(ManagedBy.AUTOMATION_API))
                    .filter(idp -> key.equals(idp.getAutomationKey()))
                    .findFirst();
            // The ACL is chosen from a non-erroring lookup so the permission gate runs before any
            // existence is revealed (domain 404 / conflict 400).
            Acl requiredAcl = match.isPresent() ? Acl.UPDATE : Acl.CREATE;
            return checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_IDENTITY_PROVIDER, requiredAcl)
                    .andThen(resolveDomain(environmentId, domainKey))
                    .flatMap(domain -> match.isPresent()
                            ? updateExisting(domain, match.get(), definition, key, principal)
                            : createNew(domain, allExisting, definition, key, principal));
        })
                .subscribe(response::resume, response::resume);
    }

    private Single<AutomationIdentityProvider> updateExisting(Domain domain, IdentityProvider existing,
            AutomationIdentityProvider definition, String key, User principal) {
        // 'system' is an immutable identity attribute (it co-determines the internal id and the system flag);
        // reject a change.
        if (definition.isSystem() != existing.isSystem()) {
            return Single.error(new InvalidParameterException(
                    "The 'system' flag is immutable for an existing identity provider '" + key
                            + "'; delete and recreate it to change it"));
        }
        if (existing.isSystem()) {
            // re-PUT is an idempotent no-op
            return Single.just(AutomationIdentityProviderMapper.toAutomationIdentityProvider(existing));
        }
        // 'type' is an immutable identity attribute; reject a change early
        if (!isBlank(definition.getType()) && !definition.getType().equals(existing.getType())) {
            return Single.error(new InvalidParameterException(
                    "The 'type' is immutable for an existing identity provider '" + key
                            + "'; delete and recreate it to change it"));
        }
        Single<AutomationIdentityProvider> rejection = rejectIfMissingIdentityProviderFields(definition, key);
        if (rejection != null) {
            return rejection;
        }
        return identityProviderManager.checkPluginDeployment(definition.getType())
                .andThen(Completable.fromAction(() ->
                        validationService.validate(definition.getType(), definition.getConfiguration())))
                .andThen(Single.defer(() -> identityProviderService.update(ReferenceType.DOMAIN, domain.getId(), existing.getId(),
                        AutomationIdentityProviderMapper.toUpdateIdentityProvider(definition), principal, false)))
                .map(AutomationIdentityProviderMapper::toAutomationIdentityProvider);
    }

    private Single<AutomationIdentityProvider> createNew(Domain domain, List<IdentityProvider> allExisting,
            AutomationIdentityProvider definition, String key, User principal) {
        final String idpId = definition.isSystem()
                ? AutomationIds.systemIdentityProviderId(domain.getId())
                : AutomationIds.identityProviderId(domain.getId(), key);
        Optional<IdentityProvider> occupant = allExisting.stream()
                .filter(idp -> idpId.equals(idp.getId()))
                .findFirst();
        if (occupant.isPresent()) {
            return Single.error(new InvalidParameterException(
                    "Identity provider key '" + key + "' conflicts with an existing identity provider"
                            + (definition.isSystem() ? " (the domain already has a system identity provider)" : "")));
        }
        if (definition.isSystem() && allExisting.stream()
                .anyMatch(idp -> idp.isManagedBy(ManagedBy.AUTOMATION_API) && idp.isSystem())) {
            return Single.error(new InvalidParameterException(
                    "The domain already has a system identity provider"));
        }
        if (definition.isSystem()) {
            return defaultIdentityProviderService.create(domain, key, principal)
                    .flatMap(created -> reconcileDomainReference(domain, key, created.getId())
                            .andThen(Single.just(created)))
                    .map(AutomationIdentityProviderMapper::toAutomationIdentityProvider);
        }
        Single<AutomationIdentityProvider> rejection = rejectIfMissingIdentityProviderFields(definition, key);
        if (rejection != null) {
            return rejection;
        }
        AutomationNewIdentityProvider newIdp = AutomationIdentityProviderMapper.toNewIdentityProvider(definition);
        newIdp.setId(idpId);
        return identityProviderManager.checkPluginDeployment(definition.getType())
                .andThen(Completable.fromAction(() ->
                        validationService.validate(definition.getType(), definition.getConfiguration())))
                .andThen(Single.defer(() -> identityProviderService.create(domain, newIdp, principal, false)))
                .map(AutomationIdentityProviderMapper::toAutomationIdentityProvider);
    }

    private static Single<AutomationIdentityProvider> rejectIfMissingIdentityProviderFields(AutomationIdentityProvider definition, String key) {
        if (isBlank(definition.getName())) {
            return Single.error(new InvalidParameterException(
                    "Field 'name' is required for a non-system identity provider '" + key + "'"));
        }
        if (isBlank(definition.getType())) {
            return Single.error(new InvalidParameterException(
                    "Field 'type' is required for a non-system identity provider '" + key + "'"));
        }
        if (isBlank(definition.getConfiguration())) {
            return Single.error(new InvalidParameterException(
                    "Field 'configuration' is required for a non-system identity provider '" + key + "'"));
        }
        return null;
    }

    /**
     * Repair a {@code accountSettings.defaultIdentityProviderForRegistration} id after a system
     * identity provider has been created. A system provider adopts the conventional
     * {@code default-idp-<domainId>} id rather than the deterministic key-based id the domain mapper
     * computes for a not-yet-existing reference.
     */
    private Completable reconcileDomainReference(Domain domain, String idpKey, String realIdpId) {
        AccountSettings account = domain.getAccountSettings();
        if (account == null
                || !idpKey.equals(account.getDefaultIdentityProviderForRegistrationKey())
                || realIdpId.equals(account.getDefaultIdentityProviderForRegistration())) {
            return Completable.complete();
        }
        account.setDefaultIdentityProviderForRegistration(realIdpId);
        return domainService.update(domain.getId(), domain, false).ignoreElement();
    }

    @Path("/{identityKey}")
    public IdentityProviderResource getIdentityProviderResource() {
        return resourceContext.getResource(IdentityProviderResource.class);
    }

    private Single<Domain> resolveDomain(String environmentId, String domainKey) {
        return domainService.findById(AutomationIds.domainId(environmentId, domainKey))
                .switchIfEmpty(Single.error(() -> new DomainNotFoundException(domainKey)))
                .flatMap(domain -> domain.isManagedBy(ManagedBy.AUTOMATION_API)
                        ? Single.just(domain)
                        : Single.error(new DomainNotFoundException(domainKey)));
    }
}
