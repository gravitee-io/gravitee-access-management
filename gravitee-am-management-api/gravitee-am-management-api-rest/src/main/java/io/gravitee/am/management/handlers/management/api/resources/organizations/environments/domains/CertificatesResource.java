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

import com.nimbusds.jose.jwk.KeyUse;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.CertificateManager;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.HashMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"certificate"})
public class CertificatesResource extends AbstractResource {

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
    @ApiOperation(value = "List registered certificates for a security domain",
            notes = "User must have the DOMAIN_CERTIFICATE[LIST] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[LIST] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[LIST] permission on the specified organization. " +
                    "Each returned certificate is filtered and contains only basic information such as id, name and type.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered certificates for a security domain", response = Certificate.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("use") String use,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_CERTIFICATE, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapPublisher(__ -> certificateService.findByDomain(domain))
                        .filter(c -> {
                            if (!StringUtils.isEmpty(use)) {
                                final JsonObject config = JsonObject.mapFrom(Json.decodeValue(c.getConfiguration(), HashMap.class));
                                if (config != null && config.getJsonArray("use") != null) {
                                    return config.getJsonArray("use").contains(use);
                                }
                            }
                            // no value, return true as sig should be the default
                            return true;
                        })
                        .map(this::filterCertificateInfos)
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                        .toList()
                        .map(sortedCertificates -> Response.ok(sortedCertificates).build()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a certificate",
            notes = "User must have the DOMAIN_CERTIFICATE[CREATE] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Certificate successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @ApiParam(name = "certificate", required = true)
            @Valid @NotNull final NewCertificate newCertificate,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_CERTIFICATE, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(schema -> certificateService.create(domain, newCertificate, authenticatedUser))
                        .map(certificate -> {
                            return Response
                                    .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/certificates/" + certificate.getId()))
                                    .entity(certificate)
                                    .build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @Path("{certificate}")
    public CertificateResource getCertificateResource() {
        return resourceContext.getResource(CertificateResource.class);
    }

    private Certificate filterCertificateInfos(Certificate certificate) {
        Certificate filteredCertificate = new Certificate();
        filteredCertificate.setId(certificate.getId());
        filteredCertificate.setName(certificate.getName());
        filteredCertificate.setType(certificate.getType());

        return filteredCertificate;
    }
}
