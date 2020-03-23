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
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.PatchApplication;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;
import static java.util.Collections.emptySet;

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
    @ApiOperation(value = "Get an application",
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

        checkPermissions(or(of(ReferenceType.APPLICATION, application, Permission.APPLICATION, Acl.READ),
                of(ReferenceType.DOMAIN, domain, Permission.APPLICATION, Acl.READ),
                of(ReferenceType.ENVIRONMENT, environmentId, Permission.APPLICATION, Acl.READ),
                of(ReferenceType.ORGANIZATION, organizationId, Permission.APPLICATION, Acl.READ)))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> applicationService.findById(application))
                        .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                        .flatMapSingle(app ->
                                permissionService.findAllPermissions(authenticatedUser, ReferenceType.APPLICATION, application)
                                        .map(applicationPermissions -> {

                                            if (!applicationPermissions.getOrDefault(Permission.APPLICATION_IDENTITY_PROVIDER, emptySet()).contains(Acl.READ)) {
                                                app.setIdentities(null);
                                            }
                                            if (!applicationPermissions.getOrDefault(Permission.APPLICATION_CERTIFICATE, emptySet()).contains(Acl.READ)) {
                                                app.setCertificate(null);
                                            }
                                            if (!applicationPermissions.getOrDefault(Permission.APPLICATION_METADATA, emptySet()).contains(Acl.READ)) {
                                                app.setMetadata(null);
                                            }
                                            if (app.getSettings() != null) {
                                                if (!applicationPermissions.getOrDefault(Permission.APPLICATION_USER_ACCOUNT, emptySet()).contains(Acl.READ)) {
                                                    app.getSettings().setAccount(null);
                                                }
                                                if (!applicationPermissions.getOrDefault(Permission.APPLICATION_SETTINGS, emptySet()).contains(Acl.READ)) {
                                                    app.getSettings().setAdvanced(null);
                                                }
                                            }

                                            return app;
                                        })))
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
    @ApiOperation(value = "Patch an application",
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
    @ApiOperation(value = "Update an application",
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
            @ApiParam(name = "client", required = true) @Valid @NotNull PatchApplication patchApplication,
            @Suspended final AsyncResponse response) {

        updateInternal(organizationId, environmentId, domain, application, patchApplication, response);
    }

    @DELETE
    @ApiOperation(value = "Delete an application",
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

        checkPermissions(or(of(ReferenceType.APPLICATION, application, Permission.APPLICATION, Acl.DELETE),
                of(ReferenceType.DOMAIN, domain, Permission.APPLICATION, Acl.DELETE),
                of(ReferenceType.ENVIRONMENT, environmentId, Permission.APPLICATION, Acl.DELETE),
                of(ReferenceType.ORGANIZATION, organizationId, Permission.APPLICATION, Acl.DELETE)))
                .andThen(applicationService.delete(application, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @POST
    @Path("secret/_renew")
    @ApiOperation(value = "Renew application secret",
            notes = "User must have APPLICATION[UPDATE] permission on the specified application " +
                    "or APPLICATION_OAUTH2[UPDATE] permission on the specified domain " +
                    "or APPLICATION_OAUTH2[UPDATE] permission on the specified environment " +
                    "or APPLICATION_OAUTH2[UPDATE] permission on the specified organization")
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

        checkPermissions(or(of(ReferenceType.APPLICATION, application, Permission.APPLICATION_OAUTH2, Acl.READ),
                of(ReferenceType.DOMAIN, domain, Permission.APPLICATION_OAUTH2, Acl.UPDATE),
                of(ReferenceType.ENVIRONMENT, environmentId, Permission.APPLICATION_OAUTH2, Acl.UPDATE),
                of(ReferenceType.ORGANIZATION, organizationId, Permission.APPLICATION_OAUTH2, Acl.UPDATE)))
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

    public void updateInternal(String organizationId, String environmentId, String domain, String application, PatchApplication patchApplication, final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkPermissions(or(of(ReferenceType.APPLICATION, application, Permission.APPLICATION, Acl.UPDATE),
                of(ReferenceType.DOMAIN, domain, Permission.APPLICATION, Acl.UPDATE),
                of(ReferenceType.ENVIRONMENT, environmentId, Permission.APPLICATION, Acl.UPDATE),
                of(ReferenceType.ORGANIZATION, organizationId, Permission.APPLICATION, Acl.UPDATE)))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(patch -> applicationService.patch(domain, application, patchApplication, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }
}