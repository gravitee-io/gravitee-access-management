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


import io.gravitee.am.management.handlers.management.api.model.PasswordPolicyEntity;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.PasswordPolicyService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewPasswordPolicy;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rafal PODLES (rafal.podles at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "Password Policy")
public class PasswordPoliciesResource extends AbstractDomainResource {

    @Autowired
    private PasswordPolicyService passwordPolicyService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listPasswordPolicies",
            summary = "List registered password policies for a security domain",
            description = "User must have the DOMAIN[READ] permission on the specified domain " +
                    "or DOMAIN[READ] permission on the specified environment " +
                    "or DOMAIN[READ] permission on the specified organization. ")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List registered password policies for a security domain", content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = PasswordPolicyEntity.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapPublisher(___ -> passwordPolicyService.findByDomain(domain))
                        .map(this::toEntity)
                        .toList())
                .subscribe(policies -> {
                            if (policies == null || policies.isEmpty()) {
                                response.resume(Response.noContent().build());
                            } else {
                                response.resume(policies);
                            }
                        },
                        response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a password policy",
            operationId = "createPasswordPolicies",
            description = "User must have the DOMAIN[UPDATE] permission on the specified domain " +
                    "or DOMAIN[UPDATE] permission on the specified environment " +
                    "or DOMAIN[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Password Policy successfully created"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Parameter(name = "passwordPolicy", required = true) @Valid @NotNull final NewPasswordPolicy newPasswordPolicy,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .map(__ -> new PasswordPolicy())
                        .map(pwdPolicy -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/password-policies/" + pwdPolicy.getId()))
                                .entity(pwdPolicy)
                                .build()))
                .subscribe(response::resume, response::resume);
    }


    @Path("{policy}")
    public PasswordPolicyResource getPasswordPolicy() {
        return resourceContext.getResource(PasswordPolicyResource.class);
    }

    private PasswordPolicyEntity toEntity(PasswordPolicy passwordPolicy) {
        PasswordPolicyEntity entity = new PasswordPolicyEntity();
        entity.setId(passwordPolicy.getId());
        entity.setName(passwordPolicy.getName());
        entity.setIsDefault(passwordPolicy.getDefaultPolicy());
        //TODO: Set with correct value AM-2892
        entity.setIdpCount(0);
        return entity;
    }
}
