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
import io.gravitee.am.management.service.ReporterServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.gravitee.am.service.model.UpdateReporter;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ReporterResource extends AbstractResource {

    private final ReporterServiceProxy reporterService;

    private final OrganizationService organizationService;

    @Inject
    public ReporterResource(ReporterServiceProxy reporterService, OrganizationService domainService) {
        this.reporterService = reporterService;
        this.organizationService = domainService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getOrgReporter",
            summary = "Get a reporter",
            description = "User must have the ORGANIZATION_REPORTER[READ] permission on the organization")

    @ApiResponse(responseCode = "200", description = "Reporter successfully fetched",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Reporter.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("reporterId") String reporterId,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_REPORTER, Acl.READ)
                .andThen(organizationService.findById(organizationId)
                        .onErrorResumeWith(Single.error(new OrganizationNotFoundException(organizationId)))
                        .flatMapMaybe(irrelevant -> reporterService.findById(reporterId))
                        .switchIfEmpty(Maybe.error(new ReporterNotFoundException(reporterId)))
                        .map(reporter -> {
                            if (!reporter.getReference().matches(ReferenceType.ORGANIZATION, organizationId)) {
                                throw new BadRequestException("Reporter does not belong to organization");
                            }
                            return Response.ok(reporter.apiRepresentation(false)).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateOrgReporter",
            summary = "Update a reporter",
            description = "User must have the ORGANIZATION_REPORTER[UPDATE] permission on the specified organization")

    @ApiResponse(responseCode = "201", description = "Reporter successfully updated",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Reporter.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("reporterId") String reporterId,
            @Parameter(name = "reporter", required = true) @Valid @NotNull UpdateReporter updateReporter,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_REPORTER, Acl.UPDATE)
                .andThen(organizationService.findById(organizationId)
                        .onErrorResumeWith(Single.error(new OrganizationNotFoundException(organizationId)))
                        .flatMap(org -> reporterService.update(Reference.organization(organizationId), reporterId, updateReporter, authenticatedUser, false)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "deleteOrgReporter",
            summary = "Delete a reporter",
            description = "User must have the ORGANIZATION_REPORTER[DELETE] permission on the specified organization")
    @ApiResponse(responseCode = "204", description = "Reporter successfully removed",
            content = @Content(mediaType = "application/json"))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("reporterId") String reporterId,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();
        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_REPORTER, Acl.READ)
                .andThen(organizationService.findById(organizationId)
                        .onErrorResumeWith(Single.error(new OrganizationNotFoundException(organizationId)))
                        .flatMapMaybe(irrelevant -> reporterService.findById(reporterId))
                        .map(Optional::ofNullable)
                        .switchIfEmpty(Maybe.just(Optional.empty()))
                        .flatMapCompletable(r -> {
                            if (r.isEmpty()) {
                                return Completable.complete();
                            }
                            if (!r.get().getReference().matches(ReferenceType.ORGANIZATION, organizationId)) {
                                return Completable.error(new BadRequestException("Reporter does not belong to organization"));
                            }

                            return reporterService.delete(reporterId, authenticatedUser);
                        }))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
