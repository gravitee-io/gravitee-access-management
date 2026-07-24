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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.model.CimdClientMetadata;
import io.gravitee.am.service.model.NewCimdApplication;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import java.util.List;

/**
 * @author Stuart Clark (stuart.clark at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "application")
public class CimdResource extends AbstractDomainResource {

    @Autowired
    private io.gravitee.am.service.cimd.CimdMetadataFetcher cimdMetadataFetcher;

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
            @ApiResponse(responseCode = "200", description = "Document validated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CimdValidationResponse.class))),
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
                            return cimdMetadataFetcher.fetchAndValidate(existingDomain, request.url());
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

    private static CimdValidationResponse toResponse(CimdClientMetadata preview) {
        return new CimdValidationResponse(
                preview.url(),
                Boolean.TRUE.equals(preview.hasInlineJwks()),
                new CimdValidationResponse.Missing(preview.missing().clientId(), preview.missing().clientName()),
                new CimdValidationResponse.ClientMetadata(
                        preview.clientId(),
                        preview.clientName(),
                        preview.redirectUris(),
                        preview.postLogoutRedirectUris(),
                        joinScope(preview.scopes()),
                        preview.grantTypes(),
                        preview.responseTypes(),
                        preview.contacts(),
                        preview.requestUris(),
                        preview.tokenEndpointAuthMethod(),
                        preview.applicationType(),
                        preview.subjectType(),
                        preview.sectorIdentifierUri(),
                        preview.idTokenSignedResponseAlg(),
                        preview.logoUri(),
                        preview.clientUri(),
                        preview.policyUri(),
                        preview.tosUri(),
                        preview.jwksUri(),
                        preview.softwareId(),
                        preview.softwareVersion(),
                        preview.softwareStatement(),
                        preview.tlsClientAuthSubjectDn(),
                        preview.tlsClientAuthSanDns(),
                        preview.tlsClientAuthSanUri(),
                        preview.tlsClientAuthSanIp(),
                        preview.tlsClientAuthSanEmail(),
                        preview.tlsClientCertificateBoundAccessTokens(),
                        preview.backchannelTokenDeliveryMode(),
                        preview.backchannelClientNotificationEndpoint(),
                        preview.backchannelAuthRequestSignAlg(),
                        preview.backchannelUserCodeParameter(),
                        preview.commandEndpoint(),
                        preview.requestObjectSigningAlg()));
    }

    private static String joinScope(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return null;
        }
        return String.join(" ", scopes);
    }

    public record CimdValidationRequest(@NotNull String url) { }

    /**
     * Response payload for the CIMD validate endpoint. The {@code metadata} object follows the
     * IANA OAuth 2.0 Dynamic Client Registration metadata names (RFC 7591 / RFC 8705 / FAPI-CIBA)
     * with {@code additionalProperties: true} so unknown registered fields are forward-compatible.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CimdValidationResponse(
            String url,
            boolean hasInlineJwks,
            Missing missing,
            ClientMetadata metadata
    ) {
        public record Missing(boolean clientId, boolean clientName) {}

        @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
                description = "OAuth 2.0 client metadata as defined in the IANA OAuth Parameters registry " +
                        "(https://www.iana.org/assignments/oauth-parameters/oauth-parameters.xhtml#client-metadata).")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ClientMetadata(
                @JsonProperty("client_id") String clientId,
                @JsonProperty("client_name") String clientName,
                @JsonProperty("redirect_uris") List<String> redirectUris,
                @JsonProperty("post_logout_redirect_uris") List<String> postLogoutRedirectUris,
                @JsonProperty("scope") String scope,
                @JsonProperty("grant_types") List<String> grantTypes,
                @JsonProperty("response_types") List<String> responseTypes,
                @JsonProperty("contacts") List<String> contacts,
                @JsonProperty("request_uris") List<String> requestUris,
                @JsonProperty("token_endpoint_auth_method") String tokenEndpointAuthMethod,
                @JsonProperty("application_type") String applicationType,
                @JsonProperty("subject_type") String subjectType,
                @JsonProperty("sector_identifier_uri") String sectorIdentifierUri,
                @JsonProperty("id_token_signed_response_alg") String idTokenSignedResponseAlg,
                @JsonProperty("logo_uri") String logoUri,
                @JsonProperty("client_uri") String clientUri,
                @JsonProperty("policy_uri") String policyUri,
                @JsonProperty("tos_uri") String tosUri,
                @JsonProperty("jwks_uri") String jwksUri,
                @JsonProperty("software_id") String softwareId,
                @JsonProperty("software_version") String softwareVersion,
                @JsonProperty("software_statement") String softwareStatement,
                @JsonProperty("tls_client_auth_subject_dn") String tlsClientAuthSubjectDn,
                @JsonProperty("tls_client_auth_san_dns") String tlsClientAuthSanDns,
                @JsonProperty("tls_client_auth_san_uri") String tlsClientAuthSanUri,
                @JsonProperty("tls_client_auth_san_ip") String tlsClientAuthSanIp,
                @JsonProperty("tls_client_auth_san_email") String tlsClientAuthSanEmail,
                @JsonProperty("tls_client_certificate_bound_access_tokens") Boolean tlsClientCertificateBoundAccessTokens,
                @JsonProperty("backchannel_token_delivery_mode") String backchannelTokenDeliveryMode,
                @JsonProperty("backchannel_client_notification_endpoint") String backchannelClientNotificationEndpoint,
                @JsonProperty("backchannel_authentication_request_signing_alg") String backchannelAuthenticationRequestSigningAlg,
                @JsonProperty("backchannel_user_code_parameter") Boolean backchannelUserCodeParameter,
                @JsonProperty("command_endpoint") String commandEndpoint,
                @JsonProperty("request_object_signing_alg") String requestObjectSigningAlg
        ) {}
    }
}
