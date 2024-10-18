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


import io.gravitee.am.model.Acl;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.PasswordPolicyService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.PasswordPolicyNotFoundException;
import io.gravitee.am.service.model.UpdatePasswordPolicy;
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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Tag(name = "Password Policy")
public class PasswordPolicyResource extends AbstractDomainResource {

    @Autowired
    private PasswordPolicyService passwordPolicyService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Read a password policy",
            operationId = "getPasswordPolicy",
            description = "User must have the DOMAIN_SETTINGS[READ] permission on the specified domain " +
                    "or DOMAIN_SETTINGS[READ] permission on the specified environment " +
                    "or DOMAIN_SETTINGS[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password Policy description"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("policy") String policy,
            @Suspended final AsyncResponse response) {
        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_SETTINGS, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(() -> new DomainNotFoundException(domain)))
                        .flatMap(__ ->
                                passwordPolicyService.findByReferenceAndId(ReferenceType.DOMAIN, domain, policy)
                                        .switchIfEmpty(Maybe.error(() -> new PasswordPolicyNotFoundException(policy)))))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a password policy",
            operationId = "updatePasswordPolicy",
            description = "User must have the DOMAIN_SETTINGS[UPDATE] permission on the specified domain " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified environment " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password Policy successfully updated"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("policy") String policy,
            @Parameter(name = "passwordPolicy", required = true) @Valid @NotNull final UpdatePasswordPolicy updatePasswordPolicy,
            @Suspended final AsyncResponse response) {
        final var authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_SETTINGS, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(() -> new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> passwordPolicyService.update(ReferenceType.DOMAIN, domain, policy, updatePasswordPolicy, authenticatedUser))
                        .doOnError(error -> log.error("Update Password Policy fails: ", error)))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("/default")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set default policy",
            operationId = "setDefaultPolicy",
            description = "User must have the DOMAIN_SETTINGS[UPDATE] permission on the specified domain " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified environment " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified organization")

    @ApiResponse(responseCode = "200", description = "Default policy updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PasswordPolicy.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void setDefaultPolicy(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("policy") String policy,
            @Suspended final AsyncResponse response) {
        final var authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_SETTINGS, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(() -> new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> passwordPolicyService.setDefaultPasswordPolicy(ReferenceType.DOMAIN, domain, policy, authenticatedUser))
                        .doOnError(error -> log.error("Update Default Password Policy fails: ", error)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete a password policy",
            operationId = "deletePasswordPolicy",
            description = "User must have the DOMAIN_SETTINGS[UPDATE] permission on the specified domain " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified environment " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password Policy successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("policy") String policy,
            @Suspended final AsyncResponse response) {
        final var authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_SETTINGS, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(() -> new DomainNotFoundException(domain)))
                        .flatMapCompletable(d -> passwordPolicyService.deleteAndUpdateIdp(ReferenceType.DOMAIN, d.getId(), policy, authenticatedUser))
                        .doOnError(error -> log.error("Delete Password Policy fails: ", error)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

}
