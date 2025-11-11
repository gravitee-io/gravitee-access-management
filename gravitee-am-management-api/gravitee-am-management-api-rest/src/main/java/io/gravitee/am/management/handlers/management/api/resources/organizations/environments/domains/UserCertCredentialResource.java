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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.CertificateCredentialService;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.CertificateCredentialAuditBuilder;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author GraviteeSource Team
 */
public class UserCertCredentialResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private CertificateCredentialService certificateCredentialService;

    @Autowired
    private AuditService auditService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getUserCertificateCredential",
            summary = "Get a user certificate credential",
            description = "User must have the DOMAIN_USER[READ] permission on the specified domain " +
                    "or DOMAIN_USER[READ] permission on the specified environment " +
                    "or DOMAIN_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User certificate credential successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CertificateCredential.class))),
            @ApiResponse(responseCode = "404", description = "Domain or certificate credential not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @PathParam("credential") String credential,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(domain -> certificateCredentialService.findById(domain, credential))
                        .switchIfEmpty(Maybe.error(new CredentialNotFoundException(credential))))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "revokeUserCertificateCredential",
            summary = "Revoke a user certificate credential",
            description = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Certificate credential successfully revoked"),
            @ApiResponse(responseCode = "404", description = "Domain or certificate credential not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void revoke(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @PathParam("credential") String credential,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(domain -> certificateCredentialService.deleteByDomainAndUserAndId(domain, user, credential)
                                .switchIfEmpty(Maybe.error(new CredentialNotFoundException(credential)))
                                .doOnSuccess(certificateCredential -> auditService.report(AuditBuilder.builder(CertificateCredentialAuditBuilder.class)
                                        .principal(authenticatedUser)
                                        .type(EventType.CREDENTIAL_DELETED)
                                        .certificateCredential(certificateCredential)))
                                .doOnError(throwable -> {
                                    // Only log audit for technical errors, not for client errors like CredentialNotFoundException or DomainNotFoundException
                                    if (!(throwable instanceof CredentialNotFoundException) && !(throwable instanceof DomainNotFoundException)) {
                                        auditService.report(AuditBuilder.builder(CertificateCredentialAuditBuilder.class)
                                                .principal(authenticatedUser)
                                                .type(EventType.CREDENTIAL_DELETED)
                                                .reference(Reference.domain(domain.getId()))
                                                .throwable(throwable));
                                    }
                                })
                                .ignoreElement()))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}

