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
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.RevokeTokenManagementService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.PatchApplication;
import io.gravitee.am.service.model.PatchApplicationType;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
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
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationResource extends AbstractResource {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private DomainService domainService;

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private RevokeTokenManagementService revokeTokenManagementService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findApplication",
            summary = "Get an application",
            description = "User must have the APPLICATION[READ] permission on the specified application " +
                    "or APPLICATION[READ] permission on the specified domain " +
                    "or APPLICATION[READ] permission on the specified environment " +
                    "or APPLICATION[READ] permission on the specified organization. " +
                    "Application will be filtered according to permissions (READ on APPLICATION_IDENTITY_PROVIDER, " +
                    "APPLICATION_CERTIFICATE, APPLICATION_METADATA, APPLICATION_USER_ACCOUNT, APPLICATION_SETTINGS)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Application.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> applicationService.findById(application))
                        .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                        .flatMapSingle(app -> findAllPermissions(authenticatedUser, organizationId, environmentId, domain, application)
                                .map(userPermissions -> filterApplicationInfos(app, userPermissions))))
                .map(application1 -> {
                    if (!application1.getDomain().equalsIgnoreCase(domain)) {
                        throw new BadRequestException("Application does not belong to domain");
                    }
                    return Response.ok(application1).build();
                })
                .subscribe(response::resume, response::resume);
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "patchApplication",
            summary = "Patch an application",
            description = "User must have APPLICATION[UPDATE] permission on the specified application " +
                    "or APPLICATION[UPDATE] permission on the specified domain " +
                    "or APPLICATION[UPDATE] permission on the specified environment " +
                    "or APPLICATION[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application successfully patched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Application.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void patch(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Parameter(name = "application", required = true) @Valid @NotNull PatchApplication patchApplication,
            @Suspended final AsyncResponse response) {

        updateInternal(organizationId, environmentId, domain, application, patchApplication, response);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateApplication",
            summary = "Update an application",
            description = "User must have APPLICATION[UPDATE] permission on the specified application " +
                    "or APPLICATION[UPDATE] permission on the specified domain " +
                    "or APPLICATION[UPDATE] permission on the specified environment " +
                    "or APPLICATION[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Application.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Parameter(name = "application", required = true) @Valid @NotNull PatchApplication patchApplication,
            @Suspended final AsyncResponse response) {

        updateInternal(organizationId, environmentId, domain, application, patchApplication, response);
    }

    @PUT
    @Path("type")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateApplicationType",
            summary = "Update an application type",
            description = "User must have APPLICATION[UPDATE] permission on the specified application " +
                    "or APPLICATION[UPDATE] permission on the specified domain " +
                    "or APPLICATION[UPDATE] permission on the specified environment " +
                    "or APPLICATION[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application type successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Application.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Parameter(name = "type", required = true) @Valid @NotNull PatchApplicationType patchApplicationType,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION, Acl.UPDATE)
                .andThen(applicationService.updateType(domain, application, patchApplicationType.getType(), authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteApplication",
            summary = "Delete an application",
            description = "User must have APPLICATION[DELETE] permission on the specified application " +
                    "or APPLICATION[DELETE] permission on the specified domain " +
                    "or APPLICATION[DELETE] permission on the specified environment " +
                    "or APPLICATION[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Application successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION, Acl.DELETE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapCompletable(exitingDomain -> applicationService.delete(application, authenticatedUser, exitingDomain)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @Path("secrets")
    public ApplicationSecretsResource getApplicationResource(){
        return resourceContext.getResource(ApplicationSecretsResource.class);
    }

    @Path("emails")
    public ApplicationEmailsResource getEmailsResource() {
        return resourceContext.getResource(ApplicationEmailsResource.class);
    }

    @Path("forms")
    public ApplicationFormsResource getFormsResource() {
        return resourceContext.getResource(ApplicationFormsResource.class);
    }

    @Path("members")
    public ApplicationMembersResource getMembersResource() {
        return resourceContext.getResource(ApplicationMembersResource.class);
    }

    @Path("resources")
    public ApplicationResourcesResource getResourcesResource() {
        return resourceContext.getResource(ApplicationResourcesResource.class);
    }

    @Path("analytics")
    public ApplicationAnalyticsResource getAnalyticsResource() {
        return resourceContext.getResource(ApplicationAnalyticsResource.class);
    }

    @Path("flows")
    public ApplicationFlowsResource getFlowsResource() {
        return resourceContext.getResource(ApplicationFlowsResource.class);
    }

    public void updateInternal(String organizationId, String environmentId, String domainId, String application, PatchApplication patchApplication, final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();
        Set<Permission> requiredPermissions = patchApplication.getRequiredPermissions();

        if (requiredPermissions.isEmpty()) {
            // If there is no require permission, it means there is nothing to update. This is not a valid request.
            response.resume(new BadRequestException("You need to specify at least one value to update."));
        } else {
            Completable.merge(requiredPermissions.stream()
                    .map(permission -> checkAnyPermission(organizationId, environmentId, domainId, application, permission, Acl.UPDATE))
                    .collect(Collectors.toList()))
                    .andThen(domainService.findById(domainId)
                            .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                            .flatMapSingle(domain -> applicationService.patch(domain, application, patchApplication, authenticatedUser, revokeTokenManagementService::deleteByApplication)
                                    .flatMap(updatedApplication -> findAllPermissions(authenticatedUser, organizationId, environmentId, domainId, application)
                                            .map(userPermissions -> filterApplicationInfos(updatedApplication, userPermissions)))))
                    .subscribe(response::resume, response::resume);
        }
    }

    private Application filterApplicationInfos(Application application, Map<ReferenceType, Map<Permission, Set<Acl>>> userPermissions) {

        Application filteredApplication = new Application();

        if (hasAnyPermission(userPermissions, Permission.APPLICATION, Acl.READ)) {
            filteredApplication.setId(application.getId());
            filteredApplication.setName(application.getName());
            filteredApplication.setType(application.getType());
            filteredApplication.setDescription(application.getDescription());
            filteredApplication.setAgentCardUrl(application.getAgentCardUrl());
            filteredApplication.setDomain(application.getDomain());
            filteredApplication.setEnabled(application.isEnabled());
            filteredApplication.setTemplate(application.isTemplate());
            filteredApplication.setCreatedAt(application.getCreatedAt());
            filteredApplication.setUpdatedAt(application.getUpdatedAt());
        }

        if (hasAnyPermission(userPermissions, Permission.APPLICATION_FACTOR, Acl.READ)) {
            filteredApplication.setFactors(application.getFactors());
        }

        if (hasAnyPermission(userPermissions, Permission.APPLICATION_IDENTITY_PROVIDER, Acl.READ)) {
            filteredApplication.setIdentityProviders(application.getIdentityProviders());
        }

        if (hasAnyPermission(userPermissions, Permission.APPLICATION_CERTIFICATE, Acl.READ)) {
            filteredApplication.setCertificate(application.getCertificate());
        }

        ApplicationSettings settings = application.getSettings();
        if (settings != null) {

            ApplicationSettings filteredApplicationSettings = new ApplicationSettings();
            filteredApplication.setSettings(filteredApplicationSettings);

            if (hasAnyPermission(userPermissions, Permission.APPLICATION_SETTINGS, Acl.READ)) {
                filteredApplication.setMetadata(application.getMetadata());
                filteredApplicationSettings.setAdvanced(settings.getAdvanced());
                filteredApplicationSettings.setAccount(settings.getAccount());
                filteredApplicationSettings.setLogin(settings.getLogin());
                filteredApplicationSettings.setPasswordSettings(settings.getPasswordSettings());
                filteredApplicationSettings.setMfa(settings.getMfa());
                filteredApplicationSettings.setCookieSettings(settings.getCookieSettings());
                filteredApplicationSettings.setRiskAssessment(settings.getRiskAssessment());
                filteredApplicationSettings.setSecretExpirationSettings(settings.getSecretExpirationSettings());
            }

            if (hasAnyPermission(userPermissions, Permission.APPLICATION_OPENID, Acl.READ)) {
                filteredApplicationSettings.setOauth(settings.getOauth());
            }

            if (hasAnyPermission(userPermissions, Permission.APPLICATION_SAML, Acl.READ)) {
                filteredApplicationSettings.setSaml(settings.getSaml());
            }
        }

        filteredApplication.setSecrets(application.getSecrets().stream().map(ClientSecret::safeSecret).toList());
        filteredApplication.setSecretSettings(application.getSecretSettings());

        return filteredApplication;
    }

    @GET
    @Path("agent-card")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "fetchAgentCard",
            summary = "Fetch agent card information",
            description = "User must have the APPLICATION[READ] permission on the specified application " +
                    "or APPLICATION[READ] permission on the specified domain " +
                    "or APPLICATION[READ] permission on the specified environment " +
                    "or APPLICATION[READ] permission on the specified organization. " +
                    "This endpoint proxies requests to the agent's .well-known/agent.json endpoint to bypass CORS restrictions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent card information",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid URL or application not found"),
            @ApiResponse(responseCode = "404", description = "Agent card URL not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void fetchAgentCard(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> applicationService.findById(application)
                                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application))))
                        .flatMapSingle(app -> {
                            // Validate that this is an AGENT application
                            if (app.getType() != io.gravitee.am.model.application.ApplicationType.AGENT) {
                                return Single.error(new BadRequestException("Agent card endpoint is only available for AGENT applications"));
                            }
                            
                            // Use the application's saved agentCardUrl
                            if (app.getAgentCardUrl() == null || app.getAgentCardUrl().trim().isEmpty()) {
                                return Single.error(new BadRequestException("No Agent Card URL configured for this application"));
                            }
                            
                            return applicationService.fetchAgentCard(app.getAgentCardUrl());
                        }))
                .subscribe(
                        response::resume,
                        error -> response.resume(error)
                );
    }
}
