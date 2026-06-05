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

import io.gravitee.am.management.handlers.automation.mapper.AutomationReporterMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationReporter;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A single reporter managed under a domain, addressed by its key.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Reporters")
public class ReporterResource extends AbstractAutomationResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private ReporterService reporterService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationGetReporter", summary = "Get a reporter",
            description = "Retrieves a single Automation-managed reporter by its key.")
    @ApiResponse(responseCode = "200", description = "The reporter",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationReporter.class)))
    @ApiResponse(responseCode = "404", description = "Domain or reporter not found, or not managed by the Automation API")
    public void get(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("reporterKey") String reporterKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_REPORTER, Acl.READ)
                .andThen(resolveDomain(environmentId, domainKey))
                .flatMap(domain -> resolveReporter(domain, reporterKey)
                        .map(AutomationReporterMapper::toAutomationReporter))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(operationId = "automationDeleteReporter", summary = "Delete a reporter",
            description = "Deletes an Automation-managed reporter by its key. Deleting a reporter that does " +
                    "not exist also returns 204.")
    @ApiResponse(responseCode = "204", description = "Reporter successfully deleted")
    public void delete(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("reporterKey") String reporterKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_REPORTER, Acl.DELETE)
                .andThen(resolveDomainMaybe(environmentId, domainKey))
                .flatMap(domain -> reporterService.findByReference(Reference.domain(domain.getId()))
                        .filter(reporter -> reporter.isManagedBy(ManagedBy.AUTOMATION_API) && reporterKey.equals(reporter.getAutomationKey()))
                        .firstElement())
                // removeSystemReporter=true: the Automation API owns the lifecycle of the resources
                // it manages, including the default, so the system guard does not apply.
                .flatMapCompletable(reporter -> reporterService.delete(reporter.getId(), principal, true))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    private Single<Domain> resolveDomain(String environmentId, String domainKey) {
        return domainService.findById(AutomationIds.domainId(environmentId, domainKey))
                .switchIfEmpty(Single.error(() -> new DomainNotFoundException(domainKey)))
                .flatMap(domain -> domain.isManagedBy(ManagedBy.AUTOMATION_API)
                        ? Single.just(domain)
                        : Single.error(new DomainNotFoundException(domainKey)));
    }

    private Maybe<Domain> resolveDomainMaybe(String environmentId, String domainKey) {
        return domainService.findById(AutomationIds.domainId(environmentId, domainKey))
                .filter(domain -> domain.isManagedBy(ManagedBy.AUTOMATION_API));
    }

    private Single<Reporter> resolveReporter(Domain domain, String reporterKey) {
        return reporterService.findByReference(Reference.domain(domain.getId()))
                .filter(reporter -> reporter.isManagedBy(ManagedBy.AUTOMATION_API) && reporterKey.equals(reporter.getAutomationKey()))
                .firstElement()
                .switchIfEmpty(Maybe.error(() -> new ReporterNotFoundException(reporterKey)))
                .toSingle();
    }
}
