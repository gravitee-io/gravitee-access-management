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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.model.GraviteeLicense;
import io.gravitee.common.http.MediaType;
import io.gravitee.node.api.license.License;
import io.gravitee.node.api.license.LicenseManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * @author GraviteeSource Team
 */
@Tag(name = "license")
public class LicenseResource extends AbstractResource {

    @Autowired
    private LicenseManager licenseManager;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private Environment environment;

    @GET
    @Operation(
            operationId = "getOrganizationLicense",
            summary = "Get the organization License, falling back to the platform License when the organization has none")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Organization license successfully fetched",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = GraviteeLicense.class))),
            @ApiResponse(responseCode = "403", description = "Not a member of the organization"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public void get(
            @PathParam("organizationId") String organizationId,
            @Suspended final AsyncResponse response) {
        if (!checkOrganizationMembership(organizationId)) {
            response.resume(new ForbiddenException("Current user is not a member of organization " + organizationId));
            return;
        }
        final boolean expirationNotifierEnabled = environment.getProperty("license.expire-notification.enabled", Boolean.class, true);
        organizationService.findById(organizationId)
                .map(organization -> {
                    final License license = licenseManager.getOrganizationLicenseOrPlatform(organizationId);
                    return GraviteeLicense.builder()
                            .tier(license.getTier())
                            .packs(license.getPacks())
                            .features(license.getFeatures())
                            .expiresAt(expirationNotifierEnabled ? license.getExpirationDate() : null)
                            .isExpired(license.isExpired())
                            .scope(license.getReferenceType())
                            .build();
                })
                .subscribe(response::resume, response::resume);
    }

    private boolean checkOrganizationMembership(String organizationId) {
        final var authenticatedUser = getAuthenticatedUser();
        final String userOrganizationId = authenticatedUser == null
                ? null
                : (String) authenticatedUser.getAdditionalInformation().getOrDefault(Claims.ORGANIZATION, Organization.DEFAULT);
        return organizationId.equals(userOrganizationId);
    }
}
