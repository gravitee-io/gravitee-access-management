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
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.gravitee.am.service.model.UpdateReporter;
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
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
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
    @ApiOperation(value = "Get a reporter",
            notes = "User must have the DOMAIN_REPORTER[READ] permission on the specified domain " +
                    "or DOMAIN_REPORTER[READ] permission on the specified environment " +
                    "or DOMAIN_REPORTER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Reporter successfully fetched", response = Reporter.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("reporter") String reporter,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_REPORTER, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> reporterService.findById(reporter))
                        .switchIfEmpty(Maybe.error(new ReporterNotFoundException(reporter)))
                        .map(reporter1 -> {
                            if (reporter1.isSystem()) {
                                reporter1.setConfiguration(null);
                            }
                            if (!reporter1.getDomain().equalsIgnoreCase(domain)) {
                                throw new BadRequestException("Reporter does not belong to domain");
                            }
                            return Response.ok(reporter1).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a reporter",
            notes = "User must have the DOMAIN_REPORTER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_REPORTER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_REPORTER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Reporter successfully updated", response = Reporter.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("reporter") String reporter,
            @ApiParam(name = "reporter", required = true) @Valid @NotNull UpdateReporter updateReporter,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_REPORTER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> reporterService.update(domain, reporter, updateReporter, authenticatedUser, false)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Delete a reporter",
            notes = "User must have the DOMAIN_REPORTER[DELETE] permission on the specified domain " +
                    "or DOMAIN_REPORTER[DELETE] permission on the specified environment " +
                    "or DOMAIN_REPORTER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Reporter successfully removed", response = Void.class),
            @ApiResponse(code = 500, message = "Internal server error")})
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
                                if (!reporter1.get().getDomain().equalsIgnoreCase(domain)) {
                                    throw new BadRequestException("Reporter does not belong to domain");
                                }

                                return reporterService.delete(reporter, authenticatedUser);
                            }
                            return Completable.complete();
                        }))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
