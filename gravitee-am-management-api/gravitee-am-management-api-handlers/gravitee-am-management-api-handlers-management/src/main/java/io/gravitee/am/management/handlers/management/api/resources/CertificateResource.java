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

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.management.service.CertificatePluginService;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Client;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain", "oauth2"})
public class CertificateResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private CertificatePluginService certificatePluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a certificate")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Certificate successfully fetched", response = Certificate.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response get(
            @PathParam("domain") String domain,
            @PathParam("certificate") String certificate) throws DomainNotFoundException {
        domainService.findById(domain);

        Certificate certificate1 = certificateService.findById(certificate);
        if (!certificate1.getDomain().equalsIgnoreCase(domain)) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Certificate does not belong to domain")
                    .build();
        }
        return Response.ok(certificate1).build();
    }

    @GET
    @Path("key")
    @ApiOperation(value = "Get the certificate public key")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Certificate key successfully fetched", response = String.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response getPublicKey(@PathParam("domain") String domain, @PathParam("certificate") String certificate) {
        CertificateProvider certificateProvider = certificateService.getCertificateProvider(domain, certificate);

        if (certificateProvider == null) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("No certificate provider found for the certificate " + certificate)
                    .build();
        }

        return Response.ok(certificateProvider.publicKey()).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a certificate")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Certificate successfully updated", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Certificate updateCertificate(
            @PathParam("domain") String domain,
            @PathParam("certificate") String certificate,
            @ApiParam(name = "certificate", required = true) @Valid @NotNull UpdateCertificate updateCertificate) {
        domainService.findById(domain);

        Certificate oldCertificate = certificateService.findById(certificate);
        String schema = certificatePluginService.getSchema(oldCertificate.getType());

        return certificateService.update(domain, certificate, updateCertificate, schema);
    }

    @DELETE
    @ApiOperation(value = "Delete a certificate")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Certificate successfully deleted"),
            @ApiResponse(code = 400, message = "Certificate is bind to existing clients"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response delete(@PathParam("domain") String domain, @PathParam("certificate") String certificate) {
        certificateService.delete(certificate);

        return Response.noContent().build();
    }
}
