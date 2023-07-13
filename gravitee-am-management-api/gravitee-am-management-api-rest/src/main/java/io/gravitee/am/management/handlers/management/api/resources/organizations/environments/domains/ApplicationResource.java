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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.PatchApplication;
import io.gravitee.am.service.model.PatchApplicationType;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "findApplication",
            value = "Get an application",
            notes = "User must have the APPLICATION[READ] permission on the specified application " +
                    "or APPLICATION[READ] permission on the specified domain " +
                    "or APPLICATION[READ] permission on the specified environment " +
                    "or APPLICATION[READ] permission on the specified organization. " +
                    "Application will be filtered according to permissions (READ on APPLICATION_IDENTITY_PROVIDER, " +
                    "APPLICATION_CERTIFICATE, APPLICATION_METADATA, APPLICATION_USER_ACCOUNT, APPLICATION_SETTINGS)")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
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
                .subscribe(response::resume, t -> response.resume(t));
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "patchApplication",
            value = "Patch an application",
            notes = "User must have APPLICATION[UPDATE] permission on the specified application " +
                    "or APPLICATION[UPDATE] permission on the specified domain " +
                    "or APPLICATION[UPDATE] permission on the specified environment " +
                    "or APPLICATION[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application successfully patched", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void patch(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @ApiParam(name = "application", required = true) @Valid @NotNull PatchApplication patchApplication,
            @Suspended final AsyncResponse response) {

        updateInternal(organizationId, environmentId, domain, application, patchApplication, response);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "updateApplication",
            value = "Update an application",
            notes = "User must have APPLICATION[UPDATE] permission on the specified application " +
                    "or APPLICATION[UPDATE] permission on the specified domain " +
                    "or APPLICATION[UPDATE] permission on the specified environment " +
                    "or APPLICATION[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application successfully updated", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @ApiParam(name = "application", required = true) @Valid @NotNull PatchApplication patchApplication,
            @Suspended final AsyncResponse response) {

        updateInternal(organizationId, environmentId, domain, application, patchApplication, response);
    }

    @PUT
    @Path("type")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "updateApplicationType",
            value = "Update an application type",
            notes = "User must have APPLICATION[UPDATE] permission on the specified application " +
                    "or APPLICATION[UPDATE] permission on the specified domain " +
                    "or APPLICATION[UPDATE] permission on the specified environment " +
                    "or APPLICATION[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application type successfully updated", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @ApiParam(name = "type", required = true) @Valid @NotNull PatchApplicationType patchApplicationType,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION, Acl.UPDATE)
                .andThen(applicationService.updateType(domain, application, patchApplicationType.getType(), authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(
            nickname = "deleteApplication",
            value = "Delete an application",
            notes = "User must have APPLICATION[DELETE] permission on the specified application " +
                    "or APPLICATION[DELETE] permission on the specified domain " +
                    "or APPLICATION[DELETE] permission on the specified environment " +
                    "or APPLICATION[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Application successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION, Acl.DELETE)
                .andThen(applicationService.delete(application, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("secret/_renew")
    @ApiOperation(
            nickname = "renewClientSecret",
            value = "Renew application secret",
            notes = "User must have APPLICATION_OPENID[UPDATE] permission on the specified application " +
                    "or APPLICATION_OPENID[UPDATE] permission on the specified domain " +
                    "or APPLICATION_OPENID[UPDATE] permission on the specified environment " +
                    "or APPLICATION_OPENID[UPDATE] permission on the specified organization")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application secret successfully updated", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void renewClientSecret(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION_OPENID, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> applicationService.renewClientSecret(domain, application, authenticatedUser)))
                .subscribe(response::resume, response::resume);
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

    public void updateInternal(String organizationId, String environmentId, String domain, String application, PatchApplication patchApplication, final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();
        Set<Permission> requiredPermissions = patchApplication.getRequiredPermissions();

        if (requiredPermissions.isEmpty()) {
            // If there is no require permission, it means there is nothing to update. This is not a valid request.
            response.resume(new BadRequestException("You need to specify at least one value to update."));
        } else {
            Completable.merge(requiredPermissions.stream()
                    .map(permission -> checkAnyPermission(organizationId, environmentId, domain, application, permission, Acl.UPDATE))
                    .collect(Collectors.toList()))
                    .andThen(domainService.findById(domain)
                            .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                            .flatMapSingle(patch -> applicationService.patch(domain, application, patchApplication, authenticatedUser)
                                    .flatMap(updatedApplication -> findAllPermissions(authenticatedUser, organizationId, environmentId, domain, application)
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
            }

            if (hasAnyPermission(userPermissions, Permission.APPLICATION_OPENID, Acl.READ)) {
                filteredApplicationSettings.setOauth(settings.getOauth());
            }

            if (hasAnyPermission(userPermissions, Permission.APPLICATION_SAML, Acl.READ)) {
                filteredApplicationSettings.setSaml(settings.getSaml());
            }
        }

        return filteredApplication;
    }
}
