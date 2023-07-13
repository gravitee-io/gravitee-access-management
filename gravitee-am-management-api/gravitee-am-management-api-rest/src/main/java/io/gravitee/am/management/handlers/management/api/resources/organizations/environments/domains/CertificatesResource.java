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
import io.gravitee.am.management.handlers.management.api.model.CertificateEntity;
import io.gravitee.am.management.handlers.management.api.model.CertificateStatus;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.CertificateServiceProxy;
import io.gravitee.am.management.service.impl.DomainNotifierServiceImpl;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.utils.CertificateTimeComparator;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
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
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"certificate"})
public class CertificatesResource extends AbstractResource {

    public static final int LATEST_SYSTEM_CERT = 1;
    @Context
    private ResourceContext resourceContext;

    @Autowired
    private CertificateServiceProxy certificateService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private Environment environment;

    private List<Integer> certificateExpiryThresholds;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "listCertificates",
            value = "List registered certificates for a security domain",
            notes = "User must have the DOMAIN_CERTIFICATE[LIST] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[LIST] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[LIST] permission on the specified organization. " +
                    "Each returned certificate is filtered and contains only basic information such as id, name and type.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered certificates for a security domain", response = CertificateEntity.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("use") String use,
            @Suspended final AsyncResponse response) {

        processExpiryThresholds();

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
                        .map(cert -> this.filterCertificateInfos(cert, certificateExpiryThresholds.get(0)))
                        .flatMapSingle(this::addApplications)
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                        .toList()
                        .map(sortedCertificates -> {

                            // compute RENEWED status for System certificates
                            sortedCertificates.stream()
                                    .filter(Certificate::isSystem)
                                    .sorted(new CertificateTimeComparator())
                                    .skip(LATEST_SYSTEM_CERT)
                                    .forEach(cert -> cert.setStatus(CertificateStatus.RENEWED));

                            return Response.ok(sortedCertificates).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    private Single<CertificateEntity> addApplications(CertificateEntity cert) {
        return applicationService.findByCertificate(cert.getId())
                .map(app -> {
                    final Application minimalAppDefinition = new Application();
                    minimalAppDefinition.setId(app.getId());
                    minimalAppDefinition.setName(app.getName());
                    return minimalAppDefinition;
                })
                .toList()
                .map(apps -> {
                    cert.setApplications(apps);
                    return cert;
                });
    }

    private void processExpiryThresholds() {
        final String expiryThresholds = environment.getProperty("services.certificate.expiryThresholds", String.class, DomainNotifierServiceImpl.DEFAULT_CERTIFICATE_EXPIRY_THRESHOLDS);
        if (this.certificateExpiryThresholds == null) {
            this.certificateExpiryThresholds = Stream.of(expiryThresholds.trim().split(","))
                    .map(String::trim)
                    .map(Integer::valueOf)
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "createCertificate",
            value = "Create a certificate",
            notes = "User must have the DOMAIN_CERTIFICATE[CREATE] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Certificate successfully created", response = CertificateEntity.class),
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
                        .map(certificate -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/certificates/" + certificate.getId()))
                                .entity(new CertificateEntity(certificate))
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("rotate")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "rotateCertificate",
            value = "Generate a new System a certificate",
            notes = "User must have the DOMAIN_CERTIFICATE[CREATE] permission on the specified domain " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified environment " +
                    "or DOMAIN_CERTIFICATE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Certificate successfully created", response = CertificateEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void rotateCertificate(@PathParam("organizationId") String organizationId,
                                @PathParam("environmentId") String environmentId,
                                @PathParam("domain") String domain,
                                @Suspended final AsyncResponse response) {
        var principal = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_CERTIFICATE, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(schema -> certificateService.rotate(domain, principal))
                        .map(certificate -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/certificates/" + certificate.getId()))
                                .entity(new CertificateEntity(certificate))
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{certificate}")
    public CertificateResource getCertificateResource() {
        return resourceContext.getResource(CertificateResource.class);
    }

    private CertificateEntity filterCertificateInfos(Certificate certificate, int certificateExpiryThreshold) {
        CertificateEntity filteredCertificate = new CertificateEntity();
        filteredCertificate.setId(certificate.getId());
        filteredCertificate.setName(certificate.getName());
        filteredCertificate.setType(certificate.getType());
        filteredCertificate.setExpiresAt(certificate.getExpiresAt());
        filteredCertificate.setCreatedAt(certificate.getCreatedAt());
        filteredCertificate.setStatus(CertificateStatus.VALID);
        filteredCertificate.setSystem(certificate.isSystem());
        if (certificate.getExpiresAt() != null) {
            final Instant now = Instant.now();
            if (certificate.getExpiresAt().getTime() <= now.toEpochMilli()) {
                filteredCertificate.setStatus(CertificateStatus.EXPIRED);
            } else if (certificate.getExpiresAt().getTime() < now.plus(certificateExpiryThreshold, ChronoUnit.DAYS).toEpochMilli()) {
                filteredCertificate.setStatus(CertificateStatus.WILL_EXPIRE);
            }
        }
        return filteredCertificate;
    }
}
