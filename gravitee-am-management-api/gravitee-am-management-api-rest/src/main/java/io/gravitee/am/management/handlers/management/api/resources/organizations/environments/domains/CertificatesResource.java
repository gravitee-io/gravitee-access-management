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
import io.gravitee.am.management.service.CertificateServiceProxy;
import io.gravitee.am.management.service.impl.CertificateEntity;
import io.gravitee.am.management.service.impl.ModifiedCertificateEntity;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "certificate")
public class CertificatesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private CertificateServiceProxy certificateFacade;

    @Autowired
    private DomainService domainService;


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listCertificates",
            summary = "List registered certificates for a security domain",
            description = "User must have the DOMAIN_CERTIFICATE[LIST] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[LIST] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[LIST] permission on the specified organization. " +
                    "Each returned certificate is filtered and contains only basic information such as id, name and type.")
    @ApiResponse(responseCode = "200", description = "List registered certificates for a security domain",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = CertificateEntity.class))))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("use") String use,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_CERTIFICATE, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(found -> certificateFacade.findByDomainAndUse(domain, use))
                        .map(sortedCertificates -> Response.ok(sortedCertificates).build()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createCertificate",
            summary = "Create a certificate",
            description = "User must have the DOMAIN_CERTIFICATE[CREATE] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified organization")
    @ApiResponse(responseCode = "201", description = "Certificate successfully created",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CertificateEntity.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Parameter(name = "certificate", required = true)
            @Valid @NotNull final NewCertificate newCertificate,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_CERTIFICATE, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(schema -> certificateFacade.create(domain, newCertificate, authenticatedUser))
                        .map(certificate -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/certificates/" + certificate.getId()))
                                .entity(ModifiedCertificateEntity.of(certificate))
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("rotate")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "rotateCertificate",
            summary = "Generate a new System a certificate",
            description = "User must have the DOMAIN_CERTIFICATE[CREATE] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified organization")
    @ApiResponse(responseCode = "201", description = "Certificate successfully created",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CertificateEntity.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void rotateCertificate(@PathParam("organizationId") String organizationId,
                                  @PathParam("environmentId") String environmentId,
                                  @PathParam("domain") String domain,
                                  @Suspended final AsyncResponse response) {
        var principal = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_CERTIFICATE, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(schema -> certificateFacade.rotate(domain, principal))
                        .map(certificate -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/certificates/" + certificate.getId()))
                                .entity(ModifiedCertificateEntity.of(certificate))
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{certificate}")
    public CertificateResource getCertificateResource() {
        return resourceContext.getResource(CertificateResource.class);
    }
}
