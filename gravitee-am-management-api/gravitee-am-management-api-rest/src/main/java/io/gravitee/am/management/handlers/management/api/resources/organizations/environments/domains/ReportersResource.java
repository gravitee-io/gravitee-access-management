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
import io.gravitee.am.management.service.ReporterPluginService;
import io.gravitee.am.management.service.ReporterServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "reporter")
public class ReportersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ReporterServiceProxy reporterService;

    @Autowired
    private ReporterPluginService reporterPluginService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listDomainReporters",
            summary = "List registered reporters for a security domain",
            description = "User must have the DOMAIN_REPORTER[LIST] permission on the specified domain " +
                    "or DOMAIN_REPORTER[LIST] permission on the specified environment " +
                    "or DOMAIN_REPORTER[LIST] permission on the specified organization. " +
                    "Except if user has DOMAIN_REPORTER[READ] permission on the domain, environment or organization, each returned reporter is filtered and contains only basic information such as id and name and type.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List registered reporters for a security domain",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = Reporter.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("userProvider") boolean userProvider,
            @Suspended final AsyncResponse response) {

        User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_REPORTER, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Single.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> reporterService.findByReference(Reference.domain(domain)).toList()))
                .flatMap(reporters ->
                    hasAnyPermission(authenticatedUser, organizationId, environmentId, domain, Permission.DOMAIN_REPORTER, Acl.READ)
                        .map(hasPermission -> {
                            reporters.stream().filter(Reporter::isSystem).forEach(reporter -> reporter.setConfiguration(null));
                            if (hasPermission) {
                                return reporters;
                            }
                            return reporters.stream().map(this::filterReporterInfos).collect(Collectors.toList());
                        })
                )
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createDomainReporter",
            summary = "Create a reporter for a security domain",
            description = "User must have the DOMAIN_REPORTER[CREATE] permission on the specified domain " +
                    "or DOMAIN_REPORTER[CREATE] permission on the specified environment " +
                    "or DOMAIN_REPORTER[CREATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reporter created for a security domain",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Reporter.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Valid NewReporter newReporter,
            @Suspended final AsyncResponse response) {

        User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_REPORTER, Acl.CREATE)
                .andThen(reporterPluginService.checkPluginDeployment(newReporter.getType()))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> reporterService.create(Reference.domain(domain), newReporter, authenticatedUser, false))
                        .map(reporter -> response.resume(Response.created(URI.create("/organizations/" + organizationId
                                            + "/environments/" + environmentId + "/domains/" + domain + "/reporters/" + reporter.getId()))
                                    .entity(reporter).build())
                    ))
                .subscribe(response::resume, response::resume);
    }

    @Path("{reporter}")
    public ReporterResource getReporterResource() {
        return resourceContext.getResource(ReporterResource.class);
    }

    private Reporter filterReporterInfos(Reporter reporter) {
        Reporter filteredReporter = new Reporter();
        filteredReporter.setId(reporter.getId());
        filteredReporter.setName(reporter.getName());
        filteredReporter.setType(reporter.getType());

        return filteredReporter;
    }
}
