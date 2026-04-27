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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.TrustDomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.TrustDomainNotFoundException;
import io.gravitee.am.service.model.UpdateTrustDomain;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

@Tag(name = "trust-domain")
public class TrustDomainResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private TrustDomainService trustDomainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getTrustDomain",
            summary = "Get a trust domain",
            description = "User must have the DOMAIN_TRUST_DOMAIN[READ] permission on the specified domain " +
                    "or DOMAIN_TRUST_DOMAIN[READ] permission on the specified environment " +
                    "or DOMAIN_TRUST_DOMAIN[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trust domain",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TrustDomain.class))),
            @ApiResponse(responseCode = "404", description = "Trust domain not found")})
    public void read(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("trustDomainId") String trustDomainId,
            @Suspended final AsyncResponse response) {
        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_TRUST_DOMAIN, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMap(domain -> trustDomainService.findById(trustDomainId)
                        .filter(td -> domainId.equals(td.getReferenceId())))
                .switchIfEmpty(Maybe.error(new TrustDomainNotFoundException(trustDomainId)))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateTrustDomain",
            summary = "Update a trust domain",
            description = "User must have the DOMAIN_TRUST_DOMAIN[UPDATE] permission on the specified domain " +
                    "or DOMAIN_TRUST_DOMAIN[UPDATE] permission on the specified environment " +
                    "or DOMAIN_TRUST_DOMAIN[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trust domain successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TrustDomain.class))),
            @ApiResponse(responseCode = "404", description = "Trust domain not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("trustDomainId") String trustDomainId,
            @Parameter(name = "trustDomain", required = true)
            @Valid @NotNull final UpdateTrustDomain updateTrustDomain,
            @Suspended final AsyncResponse response) {
        final var authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_TRUST_DOMAIN, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapSingle(domain -> trustDomainService.update(domain, trustDomainId, updateTrustDomain, authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "deleteTrustDomain",
            summary = "Delete a trust domain",
            description = "User must have the DOMAIN_TRUST_DOMAIN[DELETE] permission on the specified domain " +
                    "or DOMAIN_TRUST_DOMAIN[DELETE] permission on the specified environment " +
                    "or DOMAIN_TRUST_DOMAIN[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Trust domain successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("trustDomainId") String trustDomainId,
            @Suspended final AsyncResponse response) {
        final var authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_TRUST_DOMAIN, Acl.DELETE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapCompletable(domain -> trustDomainService.delete(domain, trustDomainId, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
