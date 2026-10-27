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

import io.gravitee.am.management.handlers.automation.mapper.AutomationApplicationMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationApplication;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
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
 * A single application managed under a domain.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Applications")
public class ApplicationResource extends AbstractAutomationResource {

    @Autowired
    private ApplicationService applicationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationGetApplication", summary = "Get an application",
            description = "Retrieves a single Automation-managed application by its key, or any application of the " +
                    "domain by id:<uuid>. Client secrets are never returned.")
    @ApiResponse(responseCode = "200", description = "The application",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationApplication.class)))
    @ApiResponse(responseCode = "404", description = "Domain or application not found, or not managed by the Automation API")
    public void get(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("applicationKey") String applicationKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(domainKey);
        final AutomationRef applicationRef = AutomationRef.parse(applicationKey);
        checkAnyPermission(principal, organizationId, environmentId, Permission.APPLICATION, Acl.READ)
                .andThen(resolver.resolveDomain(environmentId, domainRef))
                .flatMap(domain -> resolver.resolveApplication(domain, applicationRef))
                .map(AutomationApplicationMapper::toAutomationApplication)
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(operationId = "automationDeleteApplication", summary = "Delete an application",
            description = "Deletes an Automation-managed application by its key, or any application of the domain " +
                    "by id:<uuid>. Deleting an application that does not exist also returns 204.")
    @ApiResponse(responseCode = "204", description = "Application successfully deleted")
    public void delete(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("applicationKey") String applicationKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(domainKey);
        final AutomationRef applicationRef = AutomationRef.parse(applicationKey);
        checkAnyPermission(principal, organizationId, environmentId, Permission.APPLICATION, Acl.DELETE)
                .andThen(resolver.resolveDomainMaybe(environmentId, domainRef))
                .flatMapCompletable(domain -> resolver.resolveApplicationMaybe(domain, applicationRef)
                        .flatMapCompletable(app -> applicationService.delete(app.getId(), principal, domain)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
