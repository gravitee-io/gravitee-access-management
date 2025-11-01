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
import io.gravitee.am.management.service.ReporterServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.gravitee.am.service.model.UpdateReporter;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
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
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ReporterResource extends AbstractResource {

    @Autowired
    private ReporterServiceProxy reporterService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getDomainReporter",
            summary = "Get a reporter",
            description = "User must have the DOMAIN_REPORTER[READ] permission on the specified domain " +
                    "or DOMAIN_REPORTER[READ] permission on the specified environment " +
                    "or DOMAIN_REPORTER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporter successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Reporter.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("reporter") String reporterId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_REPORTER, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> reporterService.findById(reporterId))
                        .switchIfEmpty(Maybe.error(new ReporterNotFoundException(reporterId)))
                        .map(reporter -> {
                            if (!reporter.getReference().matches(ReferenceType.DOMAIN, domain)) {
                                throw new BadRequestException("Reporter does not belong to domain");
                            }
                            return Response.ok(reporter.apiRepresentation(false)).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateDomainReporter",
            summary = "Update a reporter",
            description = "User must have the DOMAIN_REPORTER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_REPORTER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_REPORTER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reporter successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Reporter.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("reporter") String reporter,
            @Parameter(name = "reporter", required = true) @Valid @NotNull UpdateReporter updateReporter,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_REPORTER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> reporterService.update(Reference.domain(domain), reporter, updateReporter, authenticatedUser, false)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "deleteDomainReporter",
            summary = "Delete a reporter",
            description = "User must have the DOMAIN_REPORTER[DELETE] permission on the specified domain " +
                    "or DOMAIN_REPORTER[DELETE] permission on the specified environment " +
                    "or DOMAIN_REPORTER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reporter successfully removed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Void.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("reporter") String reporter,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();
        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_REPORTER, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> reporterService.findById(reporter))
                        .map(Optional::ofNullable)
                        .switchIfEmpty(Maybe.just(Optional.empty()))
                        .flatMapCompletable(reporter1 -> {
                            if (reporter1.isPresent()) {
                                if (!reporter1.get().getReference().matches(ReferenceType.DOMAIN, domain)) {
                                    return Completable.error(new BadRequestException("Reporter does not belong to domain"));
                                }

                                return reporterService.delete(reporter, authenticatedUser);
                            }
                            return Completable.complete();
                        }))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
