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

import io.gravitee.am.management.handlers.automation.mapper.AutomationCertificateMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationCertificate;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.CertificateService;
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
 * A single certificate managed under a domain, addressed by its key.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Certificates")
public class CertificateResource extends AbstractAutomationResource {

    @Autowired
    private CertificateService certificateService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationGetCertificate", summary = "Get a certificate",
            description = "Retrieves a single Automation-managed certificate by its key.")
    @ApiResponse(responseCode = "200", description = "The certificate",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationCertificate.class)))
    @ApiResponse(responseCode = "404", description = "Domain or certificate not found, or not managed by the Automation API")
    public void get(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("certKey") String certKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(domainKey);
        final AutomationRef certRef = AutomationRef.parse(certKey);
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_CERTIFICATE, Acl.READ)
                .andThen(resolver.resolveDomain(environmentId, domainRef))
                .flatMap(domain -> resolver.resolveCertificate(domain, certRef)
                        .map(AutomationCertificateMapper::toAutomationCertificate))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(operationId = "automationDeleteCertificate", summary = "Delete a certificate",
            description = "Deletes an Automation-managed certificate by its key. Deleting a certificate that " +
                    "does not exist also returns 204.")
    @ApiResponse(responseCode = "204", description = "Certificate successfully deleted")
    public void delete(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("certKey") String certKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(domainKey);
        final AutomationRef certRef = AutomationRef.parse(certKey);
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_CERTIFICATE, Acl.DELETE)
                .andThen(resolver.resolveDomainMaybe(environmentId, domainRef))
                .flatMap(domain -> resolver.resolveCertificateMaybe(domain, certRef))
                .flatMapCompletable(certificate -> certificateService.delete(certificate.getId(), principal))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
