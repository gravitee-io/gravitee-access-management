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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
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
    @ApiOperation(value = "Get an application")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.APPLICATION_SETTINGS, acls = RolePermissionAction.READ)
    })
    public void get(
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(irrelevant -> applicationService.findById(application))
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                .map(application1 -> {
                    if (!application1.getDomain().equalsIgnoreCase(domain)) {
                        throw new BadRequestException("Application does not belong to domain");
                    }
                    return Response.ok(application1).build();
                })
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Patch an application")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application successfully patched", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.APPLICATION_SETTINGS, acls = RolePermissionAction.UPDATE)
    })
    public void patch(
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @ApiParam(name = "application", required = true) @Valid @NotNull PatchApplication patchApplication,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(__ -> applicationService.patch(domain, application, patchApplication, authenticatedUser))
                .map(application1 -> Response.ok(application1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update an application")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application successfully updated", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.APPLICATION_SETTINGS, acls = RolePermissionAction.UPDATE)
    })
    public void update(
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @ApiParam(name = "client", required = true) @Valid @NotNull PatchApplication patchApplication,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                //.flatMapSingle(__ -> this.applyDefaultResponseType(patchClient))
                .flatMapSingle(patch -> applicationService.patch(domain, application, patchApplication, authenticatedUser))
                .map(updatedApplication -> Response.ok(updatedApplication).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Delete an application")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Application successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.APPLICATION_SETTINGS, acls = RolePermissionAction.DELETE)
    })
    public void delete(@PathParam("domain") String domain,
                       @PathParam("application") String application,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        applicationService.delete(application, authenticatedUser)
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }

    @POST
    @Path("secret/_renew")
    @ApiOperation(value = "Renew application secret")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application secret successfully updated", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.APPLICATION_OAUTH2, acls = RolePermissionAction.UPDATE)
    })
    public void renewClientSecret(@PathParam("domain") String domain,
                                  @PathParam("application") String application,
                                  @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(__ -> applicationService.renewClientSecret(domain, application, authenticatedUser))
                .map(updatedApp -> Response.ok(updatedApp).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
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
}
