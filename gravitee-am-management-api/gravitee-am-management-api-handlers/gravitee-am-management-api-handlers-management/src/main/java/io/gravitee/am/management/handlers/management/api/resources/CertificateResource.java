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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.CertificateManager;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Client;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateManager certificateManager;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a certificate")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Certificate successfully fetched", response = Certificate.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("domain") String domain,
            @PathParam("certificate") String certificate,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(irrelevant -> certificateService.findById(certificate))
                .switchIfEmpty(Maybe.error(new CertificateNotFoundException(certificate)))
                .map(certificate1 -> {
                    if (!certificate1.getDomain().equalsIgnoreCase(domain)) {
                        throw new BadRequestException("Certificate does not belong to domain");
                    }
                    return Response.ok(certificate1).build();
                })
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @GET
    @Path("key")
    @ApiOperation(value = "Get the certificate public key")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Certificate key successfully fetched", response = String.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void getPublicKey(@PathParam("domain") String domain,
                             @PathParam("certificate") String certificate,
                             @Suspended final AsyncResponse response) {
        certificateManager.getCertificateProvider(certificate)
                .switchIfEmpty(Maybe.error(new BadRequestException("No certificate provider found for the certificate " + certificate)))
                .flatMapSingle(certificateProvider -> certificateProvider.publicKey())
                .map(publicKey -> Response.ok(publicKey).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a certificate")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Certificate successfully updated", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateCertificate(
            @PathParam("domain") String domain,
            @PathParam("certificate") String certificate,
            @ApiParam(name = "certificate", required = true) @Valid @NotNull UpdateCertificate updateCertificate,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(schema -> certificateService.update(domain, certificate, updateCertificate, authenticatedUser))
                .map(certificate1 -> Response.ok(certificate1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Delete a certificate")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Certificate successfully deleted"),
            @ApiResponse(code = 400, message = "Certificate is bind to existing clients"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(@PathParam("domain") String domain,
                       @PathParam("certificate") String certificate,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        certificateService.delete(certificate, authenticatedUser)
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }
}
