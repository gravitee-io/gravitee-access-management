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

import io.gravitee.am.certificate.api.CertificateKey;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.CertificateManager;
import io.gravitee.am.management.service.CertificateServiceProxy;
import io.gravitee.am.management.service.impl.CertificateEntity;
import io.gravitee.am.management.service.impl.ModifiedCertificateEntity;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateResource extends AbstractResource {

    @Autowired
    private CertificateServiceProxy certificateService;

    @Autowired
    private CertificateManager certificateManager;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findCertificate",
            summary = "Get a certificate",
            description = "User must have the DOMAIN_CERTIFICATE[READ] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[READ] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Certificate successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CertificateEntity.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("certificate") String certificate,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_CERTIFICATE, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> certificateService.findById(certificate))
                        .switchIfEmpty(Maybe.error(new CertificateNotFoundException(certificate)))
                        .map(cert -> {
                            if (!cert.getDomain().equalsIgnoreCase(domain)) {
                                throw new BadRequestException("Certificate does not belong to domain");
                            }
                            return Response.ok(ModifiedCertificateEntity.of(cert)).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("key")
    @Operation(
            operationId = "getCertificatePublicKey",
            summary = "Get the certificate public key",
            description = "User must have the DOMAIN[READ] permission on the specified domain " +
                    "or DOMAIN[READ] permission on the specified environment " +
                    "or DOMAIN[READ] permission on the specified organization")
    @ApiResponse(responseCode = "200", description = "Certificate key successfully fetched",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void getPublicKey(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("certificate") String certificate,
            @Suspended final AsyncResponse response) {

        // FIXME: should we create a DOMAIN_CERTIFICATE_KEY permission instead ?
        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN, Acl.READ)
                .andThen(certificateManager.getCertificateProvider(certificate)
                        .switchIfEmpty(Maybe.error(new BadRequestException("No certificate provider found for the certificate " + certificate)))
                        .flatMapSingle(CertificateProvider::publicKey))
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("keys")
    @Operation(
            operationId = "getCertificatePublicKeys",
            summary = "Get the certificate public keys",
            description = "User must have the DOMAIN[READ] permission on the specified domain " +
                    "or DOMAIN[READ] permission on the specified environment " +
                    "or DOMAIN[READ] permission on the specified organization")

    @ApiResponse(responseCode = "200", description = "Certificate keys successfully fetched",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = CertificateKey.class))))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void getPublicKeys(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("certificate") String certificate,
            @Suspended final AsyncResponse response) {

        // FIXME: should we create a DOMAIN_CERTIFICATE_KEY permission instead ?
        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN, Acl.READ)
                .andThen(certificateManager.getCertificateProvider(certificate)
                        .switchIfEmpty(Maybe.error(new BadRequestException("No certificate provider found for the certificate " + certificate)))
                        .flatMapSingle(CertificateProvider::publicKeys))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateCertificate",
            summary = "Update a certificate",
            description = "User must have the DOMAIN_CERTIFICATE[UPDATE] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[UPDATE] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[UPDATE] permission on the specified organization")

    @ApiResponse(responseCode = "201", description = "Certificate successfully updated",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ModifiedCertificateEntity.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void updateCertificate(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("certificate") String certificate,
            @Parameter(name = "certificate", required = true) @Valid @NotNull UpdateCertificate updateCertificate,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_CERTIFICATE, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(schema -> certificateService.update(domain, certificate, updateCertificate, authenticatedUser))
                        .map(certificate1 -> Response.ok(ModifiedCertificateEntity.of(certificate1)).build()))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteCertificate",
            summary = "Delete a certificate",
            description = "User must have the DOMAIN_CERTIFICATE[DELETE] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[DELETE] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[DELETE] permission on the specified organization")

    @ApiResponse(responseCode = "204", description = "Certificate successfully deleted")
    @ApiResponse(responseCode = "400", description = "Certificate is bind to existing clients")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("certificate") String certificate,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_CERTIFICATE, Acl.DELETE)
                .andThen(certificateService.delete(certificate, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
