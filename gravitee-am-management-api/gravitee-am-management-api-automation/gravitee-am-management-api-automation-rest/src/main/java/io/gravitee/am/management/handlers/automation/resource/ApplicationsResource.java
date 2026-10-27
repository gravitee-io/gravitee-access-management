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
import io.gravitee.am.management.handlers.automation.mapper.AutomationApplicationMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationApplication;
import io.gravitee.am.management.service.RevokeTokenManagementService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Applications managed individually under a domain.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Applications")
public class ApplicationsResource extends AbstractAutomationResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private RevokeTokenManagementService revokeTokenManagementService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationListApplications", summary = "List a domain's applications",
            description = "Returns all applications managed by the Automation API under the domain. Applications " +
                    "created outside the Automation API are not returned. Client secrets are never returned.")
    @ApiResponse(responseCode = "200", description = "List of applications",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AutomationApplication.class))))
    @ApiResponse(responseCode = "404", description = "Domain not found, or not managed by the Automation API")
    public void list(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.APPLICATION, Acl.LIST)
                .andThen(resolver.resolveDomain(environmentId, AutomationRef.parse(domainKey)))
                .flatMap(domain -> applicationService.findByDomain(domain.getId()))
                .map(applications -> applications.stream()
                        .filter(app -> app.isManagedBy(ManagedBy.AUTOMATION_API))
                        .sorted(Comparator.comparing(app -> nullToEmpty(app.getAutomationKey()), String.CASE_INSENSITIVE_ORDER))
                        .map(AutomationApplicationMapper::toAutomationApplication)
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationCreateOrUpdateApplication",
            summary = "Create or update an application",
            description = "Idempotent create-or-update. Uses the key field in the body to identify the application " +
                    "within the domain. On first apply the application is created; subsequent applies update it to " +
                    "the desired state: omitted fields are reset, and computed fields are computed again. " +
                    "No client secret is ever generated nor returned. " +
                    "The certificate and the identity providers are references (key or id:<uuid>) that are not " +
                    "checked for existence; the scopes must exist in the domain. " +
                    "The clientId is immutable once set. The type can be changed, except to or from agent. " +
                    "Disabling the application revokes its tokens. " +
                    "When dryRun is true, the endpoint validates the payload without persisting; the returned " +
                    "application carries a dryRunErrors list (absent on success, populated with validation errors " +
                    "otherwise).")
    @ApiResponse(responseCode = "200", description = "The created or updated application. When dryRun is true the " +
            "application includes a dryRunErrors field with any validation errors.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationApplication.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request: a key conflict, an invalid setting, an unknown " +
            "scope, a duplicate or changed clientId, or a type change to or from agent")
    @ApiResponse(responseCode = "404", description = "Domain not found, or not managed by the Automation API; or an " +
            "id:<uuid> application that does not exist")
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Desired state of the application.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutomationApplication.class),
                            examples = @ExampleObject(name = "Mobile application",
                                    description = "A native mobile application using the authorization code flow with PKCE",
                                    value = "{\"key\":\"mobile-app\",\"name\":\"Mobile app\",\"type\":\"native\"," +
                                            "\"certificate\":\"signing-cert\"," +
                                            "\"identityProviders\":[{\"identity\":\"customer-users\",\"priority\":0}]," +
                                            "\"settings\":{\"oauth\":{" +
                                            "\"grantTypes\":[\"authorization_code\",\"refresh_token\"]," +
                                            "\"redirectUris\":[\"com.example.app:/callback\"]," +
                                            "\"scopeSettings\":[{\"scope\":\"openid\",\"defaultScope\":true},{\"scope\":\"profile\"}]," +
                                            "\"tokenEndpointAuthMethod\":\"none\",\"forcePKCE\":true}}}")))
            @Valid @NotNull AutomationApplication definition,
            @Parameter(description = "When true, validates the payload without persisting. The returned application includes a dryRunErrors field.")
            @QueryParam("dryRun") @DefaultValue("false") boolean dryRun,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(domainKey);
        final AutomationRef appRef = AutomationRef.parse(definition.getAutomationKey());

        Single<AutomationApplication> result = createOrUpdate(organizationId, environmentId, domainRef, appRef, definition, principal, dryRun);
        if (dryRun) {
            result = result.onErrorResumeNext(ex -> Single.just(withErrors(definition, ex)));
        }
        result.subscribe(response::resume, response::resume);
    }

    private Single<AutomationApplication> createOrUpdate(String organizationId, String environmentId, AutomationRef domainRef,
            AutomationRef appRef, AutomationApplication definition, User principal, boolean dryRun) {
        // An 'id:' body addresses a preexisting application directly (update-only)
        if (appRef instanceof AutomationRef.IdRef) {
            return checkAnyPermission(principal, organizationId, environmentId, Permission.APPLICATION, Acl.UPDATE)
                    .andThen(resolver.resolveDomain(environmentId, domainRef))
                    .flatMap(domain -> resolver.resolveApplication(domain, appRef)
                            .flatMap(existing -> update(domain, existing, definition, appRef.raw(), principal, dryRun)));
        }

        final String key = appRef.raw();
        final String domainId = AutomationIds.domainId(environmentId, domainRef);
        return applicationService.findById(domainId, AutomationIds.applicationId(domainId, key))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(found -> {
                    Optional<Application> match = found
                            .filter(app -> app.isManagedBy(ManagedBy.AUTOMATION_API) && key.equals(app.getAutomationKey()));
                    // The ACL is chosen from a non-erroring lookup so the permission gate runs before any
                    // existence is revealed (domain 404 / conflict 400).
                    Acl requiredAcl = match.isPresent() ? Acl.UPDATE : Acl.CREATE;
                    return checkAnyPermission(principal, organizationId, environmentId, Permission.APPLICATION, requiredAcl)
                            .andThen(resolver.resolveDomain(environmentId, domainRef))
                            .flatMap(domain -> {
                                if (match.isPresent()) {
                                    return update(domain, match.get(), definition, key, principal, dryRun);
                                }
                                if (found.isPresent()) {
                                    return Single.error(new InvalidParameterException(
                                            "Application key '" + key + "' conflicts with an existing application"));
                                }
                                return create(domain, definition, key, principal, dryRun);
                            });
                });
    }

    private Single<AutomationApplication> create(Domain domain, AutomationApplication definition, String key, User principal, boolean dryRun) {
        return identityProviders(domain)
                .flatMap(idps -> {
                    Application application = AutomationApplicationMapper.newApplication(domain, key, definition, idps);
                    return dryRun
                            ? applicationService.validateCreate(domain, application)
                            : applicationService.create(domain, application, principal);
                })
                .map(AutomationApplicationMapper::toAutomationApplication);
    }

    private Single<AutomationApplication> update(Domain domain, Application existing, AutomationApplication definition,
            String key, User principal, boolean dryRun) {
        if (definition.getClientId() != null && !definition.getClientId().equals(existing.clientId())) {
            return Single.error(new InvalidParameterException(
                    "The 'clientId' is immutable for an existing application '" + key + "'"));
        }
        if (definition.getType() != existing.getType()
                && (definition.getType() == ApplicationType.AGENT || existing.getType() == ApplicationType.AGENT)) {
            return Single.error(new InvalidParameterException(
                    "The type of application '" + key + "' cannot be changed to or from agent; delete and recreate it"));
        }
        return identityProviders(domain)
                .flatMap(idps -> {
                    // a type change applies the new type's template first, as PUT /applications/{id}/type does
                    Application desired = definition.getType() != existing.getType()
                            ? applicationService.changeType(existing, definition.getType())
                            : new Application(existing);
                    AutomationApplicationMapper.applyTo(definition, desired, domain, idps);
                    return dryRun
                            ? applicationService.validateUpdate(domain, existing, desired)
                            : applicationService.update(domain, existing, desired, principal, revokeTokens(principal));
                })
                .map(AutomationApplicationMapper::toAutomationApplication);
    }

    private Single<List<IdentityProvider>> identityProviders(Domain domain) {
        return identityProviderService.findAll(ReferenceType.DOMAIN, domain.getId()).toList();
    }

    private BiFunction<Domain, Application, Completable> revokeTokens(User principal) {
        return (domain, application) -> revokeTokenManagementService.sendProcessRequest(domain,
                RevokeToken.byApplication(domain, application, principal.getId(), principal.getUsername()));
    }

    @Path("/{applicationKey}")
    public ApplicationResource getApplicationResource() {
        return resourceContext.getResource(ApplicationResource.class);
    }
}
