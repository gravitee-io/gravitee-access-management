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
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.AutomationNewCertificate;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

/**
 * Certificates managed individually under a domain.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Certificates")
public class CertificatesResource extends AbstractAutomationResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private CertificateService certificateService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationListCertificates", summary = "List a domain's certificates",
            description = "Returns all certificates managed by the Automation API under the domain. Certificates " +
                    "created outside the Automation API are not returned.")
    @ApiResponse(responseCode = "200", description = "List of certificates",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AutomationCertificate.class))))
    @ApiResponse(responseCode = "404", description = "Domain not found, or not managed by the Automation API")
    public void list(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_CERTIFICATE, Acl.LIST)
                .andThen(resolveDomain(environmentId, domainKey))
                .flatMap(domain -> certificateService.findByDomain(domain.getId())
                        .filter(certificate -> certificate.isManagedBy(ManagedBy.AUTOMATION_API))
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(
                                nullToEmpty(o1.getAutomationKey()), nullToEmpty(o2.getAutomationKey())))
                        .map(AutomationCertificateMapper::toAutomationCertificate)
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationCreateOrUpdateCertificate",
            summary = "Create or update a certificate",
            description = "Idempotent create-or-update. Uses the key field in the body to identify the " +
                    "certificate within the domain. Re-applying an unchanged definition is a no-op. The system " +
                    "flag is immutable; changing it requires deleting and recreating the certificate.")
    @ApiResponse(responseCode = "200", description = "The created or updated certificate",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationCertificate.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request: a key conflict, a missing required " +
            "field for a non-system certificate, an attempt to change the immutable system flag, or a second " +
            "system certificate for the domain")
    @ApiResponse(responseCode = "404", description = "Domain not found, or not managed by the Automation API")
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Desired state of the certificate. For a system certificate, supply only " +
                            "system: true and key.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutomationCertificate.class),
                            examples = {
                                    @ExampleObject(name = "Certificate", description = "A fully specified certificate",
                                            value = "{\"key\":\"signing-cert\",\"name\":\"Signing certificate\"," +
                                                    "\"type\":\"javakeystore-am-certificate\"," +
                                                    "\"configuration\":\"{\\\"jks\\\":{\\\"content\\\":\\\"...\\\",\\\"name\\\":\\\"keystore.jks\\\"},\\\"storepass\\\":\\\"secret\\\",\\\"alias\\\":\\\"mykey\\\",\\\"keypass\\\":\\\"secret\\\"}\"}"),
                                    @ExampleObject(name = "System certificate", description = "The domain's system certificate; only system and key are needed",
                                            value = "{\"key\":\"default\",\"system\":true}")
                            }))
            @Valid @NotNull AutomationCertificate definition,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final String domainId = AutomationIds.domainId(environmentId, domainKey);
        final String key = definition.getAutomationKey();
        certificateService.findByDomain(domainId).toList().flatMap(allExisting -> {
            Optional<Certificate> match = allExisting.stream()
                    .filter(certificate -> certificate.isManagedBy(ManagedBy.AUTOMATION_API))
                    .filter(certificate -> key.equals(certificate.getAutomationKey()))
                    .findFirst();
            // The ACL is chosen from a non-erroring lookup so the permission gate runs before any
            // existence is revealed (domain 404 / conflict 400).
            Acl requiredAcl = match.isPresent() ? Acl.UPDATE : Acl.CREATE;
            return checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_CERTIFICATE, requiredAcl)
                    .andThen(resolveDomain(environmentId, domainKey))
                    .flatMap(domain -> {
                        if (match.isPresent()) {
                            Certificate existing = match.get();
                            // 'system' is an immutable identity attribute; reject a change.
                            if (definition.isSystem() != existing.isSystem()) {
                                return Single.error(new InvalidParameterException(
                                        "The 'system' flag is immutable for an existing certificate '" + key
                                                + "'; delete and recreate it to change it"));
                            }
                            if (existing.isSystem()) {
                                // re-PUT is an idempotent no-op
                                return Single.just(AutomationCertificateMapper.toAutomationCertificate(existing));
                            }
                            return certificateService.update(domain, existing.getId(),
                                            AutomationCertificateMapper.toUpdateCertificate(definition), principal)
                                    .map(AutomationCertificateMapper::toAutomationCertificate);
                        }

                        final String certId = AutomationIds.certificateId(domain.getId(), key);
                        Optional<Certificate> occupant = allExisting.stream()
                                .filter(certificate -> certId.equals(certificate.getId()))
                                .findFirst();
                        if (occupant.isPresent()) {
                            return Single.error(new InvalidParameterException(
                                    "Certificate key '" + key + "' conflicts with an existing certificate"));
                        }
                        if (definition.isSystem() && allExisting.stream()
                                .anyMatch(certificate -> certificate.isManagedBy(ManagedBy.AUTOMATION_API) && certificate.isSystem())) {
                            return Single.error(new InvalidParameterException(
                                    "The domain already has a system certificate"));
                        }
                        if (definition.isSystem()) {
                            return certificateService.createSystem(domain, certId, key, principal)
                                    .map(AutomationCertificateMapper::toAutomationCertificate);
                        }
                        Single<AutomationCertificate> rejection = rejectIfMissingCertificateFields(definition, key);
                        if (rejection != null) {
                            return rejection;
                        }
                        AutomationNewCertificate newCertificate = AutomationCertificateMapper.toNewCertificate(definition);
                        newCertificate.setId(certId);
                        return certificateService.create(domain, newCertificate, principal, false)
                                .map(AutomationCertificateMapper::toAutomationCertificate);
                    });
        })
                .subscribe(response::resume, response::resume);
    }

    private static Single<AutomationCertificate> rejectIfMissingCertificateFields(AutomationCertificate definition, String key) {
        if (isBlank(definition.getName())) {
            return Single.error(new InvalidParameterException(
                    "Field 'name' is required for a non-system certificate '" + key + "'"));
        }
        if (isBlank(definition.getType())) {
            return Single.error(new InvalidParameterException(
                    "Field 'type' is required for a non-system certificate '" + key + "'"));
        }
        if (isBlank(definition.getConfiguration())) {
            return Single.error(new InvalidParameterException(
                    "Field 'configuration' is required for a non-system certificate '" + key + "'"));
        }
        return null;
    }

    @Path("/{certKey}")
    public CertificateResource getCertificateResource() {
        return resourceContext.getResource(CertificateResource.class);
    }

    private Single<Domain> resolveDomain(String environmentId, String domainKey) {
        return domainService.findById(AutomationIds.domainId(environmentId, domainKey))
                .switchIfEmpty(Single.error(() -> new DomainNotFoundException(domainKey)))
                .flatMap(domain -> domain.isManagedBy(ManagedBy.AUTOMATION_API)
                        ? Single.just(domain)
                        : Single.error(new DomainNotFoundException(domainKey)));
    }
}
