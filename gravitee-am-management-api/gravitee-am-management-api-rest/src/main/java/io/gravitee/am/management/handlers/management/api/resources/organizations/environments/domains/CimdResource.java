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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.model.CimdPreview;
import io.gravitee.am.service.model.NewCimdApplication;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * @author Stuart Clark (stuart.clark at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "application")
public class CimdResource extends AbstractDomainResource {

    @Autowired
    private CimdMetadataDocumentService cimdMetadataDocumentService;

    @Autowired
    private ApplicationService applicationService;

    @POST
    @Path("validate")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "validateCimdUrl",
            summary = "Validate a CIMD URL and return parsed metadata preview",
            description = "User must have APPLICATION[CREATE] permission on the specified domain")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document validated"),
            @ApiResponse(responseCode = "400", description = "Document invalid or untrusted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void validate(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Valid @NotNull final CimdValidationRequest request,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION, Acl.CREATE)
                .andThen(checkDomainExists(domain)
                        .flatMap(existingDomain -> {
                            if (existingDomain.getOidc() == null
                                    || existingDomain.getOidc().getCimdSettings() == null
                                    || !existingDomain.getOidc().getCimdSettings().isEnabled()) {
                                throw new InvalidClientMetadataException("CIMD is not enabled for this domain.");
                            }
                            return cimdMetadataDocumentService.fetchAndValidate(existingDomain, request.url());
                        }))
                .map(CimdResource::toResponse)
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("applications")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createApplicationFromCimd",
            summary = "Create an application from a CIMD document URL",
            description = "User must have APPLICATION[CREATE] permission on the specified domain")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application successfully created"),
            @ApiResponse(responseCode = "400", description = "Document invalid or untrusted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void createApplication(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Valid @NotNull final NewCimdApplication newApplication,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION, Acl.CREATE)
                .andThen(checkDomainExists(domain)
                        .flatMap(existingDomain -> {
                            if (existingDomain.getOidc() == null
                                    || existingDomain.getOidc().getCimdSettings() == null
                                    || !existingDomain.getOidc().getCimdSettings().isEnabled()) {
                                throw new InvalidClientMetadataException("CIMD is not enabled for this domain.");
                            }
                            return applicationService.createFromCimd(existingDomain, newApplication, authenticatedUser);
                        })
                        .map(application -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/applications/" + application.getId()))
                                .entity(application)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    private static CimdValidationResponse toResponse(CimdPreview preview) {
        return new CimdValidationResponse(
                preview.url(),
                preview.clientId(),
                preview.clientName(),
                preview.redirectUris(),
                preview.scopes(),
                preview.grantTypes(),
                preview.responseTypes(),
                preview.tokenEndpointAuthMethod(),
                preview.logoUri(),
                preview.jwksUri(),
                new CimdValidationResponse.Missing(preview.missing().clientId(), preview.missing().clientName())
        );
    }

    public record CimdValidationRequest(@NotNull String url) { }

    public record CimdValidationResponse(
            String url,
            String clientId,
            String clientName,
            java.util.List<String> redirectUris,
            java.util.List<String> scopes,
            java.util.List<String> grantTypes,
            java.util.List<String> responseTypes,
            String tokenEndpointAuthMethod,
            String logoUri,
            String jwksUri,
            Missing missing
    ) {
        public record Missing(boolean clientId, boolean clientName) {}
    }
}
