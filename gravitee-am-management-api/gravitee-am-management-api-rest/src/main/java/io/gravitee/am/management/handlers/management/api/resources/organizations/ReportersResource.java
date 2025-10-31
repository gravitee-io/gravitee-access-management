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
package io.gravitee.am.management.handlers.management.api.resources.organizations;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.ReporterPluginService;
import io.gravitee.am.management.service.ReporterServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Tag(name = "reporter")
public class ReportersResource extends AbstractResource {
    @Context
    private ResourceContext resourceContext;
    private final ReporterServiceProxy reporterService;
    private final ReporterPluginService reporterPluginService;
    private final OrganizationService organizationService;

    @Inject
    public ReportersResource(ReporterServiceProxy reporterService,
                             ReporterPluginService reporterPluginService,
                             OrganizationService organizationService) {
        this.reporterService = reporterService;
        this.reporterPluginService = reporterPluginService;
        this.organizationService = organizationService;
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getOrgReporters",
            summary = "List registered reporters for a security domain",
            description = "User must have the ORGANIZATION_REPORTER[LIST] permission on the specified organization. " +
                    "Except if user has ORGANIZATION_REPORTER[READ] permission on the organization, each returned reporter is filtered and contains only basic information such as id and name and type.")
    @ApiResponse(responseCode = "200", description = "List registered reporters for an organization",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = Reporter.class))))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void list(
            @PathParam("organizationId") String organizationId,
            @Suspended final AsyncResponse response) {

        User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_REPORTER, Acl.LIST)
                .andThen(reporterService.findByReference(Reference.organization(organizationId))
                        .toList()
                )
                .flatMap(reporters ->
                        hasPermission(authenticatedUser, ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_REPORTER, Acl.READ)
                                .map(hasPermission -> reporters.stream()
                                        .map(reporter -> reporter.apiRepresentation(!hasPermission))
                                        .toList())
                )
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createOrgReporter",
            summary = "Create a reporter for an organization",
            description = "User must have the ORGANIZATION_REPORTER[CREATE] permission on the specified organization")
    @ApiResponse(responseCode = "201", description = "Reporter created for a security domain",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Reporter.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void create(
            @PathParam("organizationId") String organizationId,
            @Valid NewReporter newReporter,
            @Suspended final AsyncResponse response) {

        User authenticatedUser = getAuthenticatedUser();
        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_REPORTER, Acl.CREATE)
                .andThen(reporterPluginService.checkPluginDeployment(newReporter.getType()))
                .andThen(organizationService.findById(organizationId)
                        .onErrorResumeWith(Single.error(new OrganizationNotFoundException(organizationId))))
                .flatMap(org -> reporterService.create(Reference.organization(organizationId), newReporter, authenticatedUser, false))
                .map(reporter -> Response.created(URI.create("/organizations/%s/reporters/%s".formatted(organizationId, reporter.getId())))
                        .entity(reporter)
                        .build())
                .subscribe(response::resume, response::resume);
    }

    @Path("{reporterId}")
    @Operation(summary = "Get a reporter by its identifier", operationId = "getReporter")
    public ReporterResource getReporterResource() {
        return resourceContext.getResource(ReporterResource.class);
    }
}
