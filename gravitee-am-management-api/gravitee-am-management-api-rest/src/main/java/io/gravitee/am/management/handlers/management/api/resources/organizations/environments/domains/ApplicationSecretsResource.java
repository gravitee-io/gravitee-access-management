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
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationSecretService;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewClientSecret;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

public class ApplicationSecretsResource extends AbstractResource {

    @Autowired
    private ApplicationSecretService applicationSecretService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private DomainService domainService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listSecrets",
            summary = "List secrets of an application",
            description = "User must have APPLICATION_OPENID[LIST] permission on the specified application " +
                    "or APPLICATION_OPENID[LIST] permission on the specified domain " +
                    "or APPLICATION_OPENID[LIST] permission on the specified environment " +
                    "or APPLICATION_OPENID[LIST] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List secrets of an application",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ClientSecret.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getSecrets(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION_OPENID, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> applicationService.findById(application))
                        .switchIfEmpty(Single.error(new ApplicationNotFoundException(application)))
                        .flatMap(application1 -> applicationSecretService.findAllByApplication(application1).toList()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createSecret",
            summary = "Create a secret for an application",
            description = "User must have APPLICATION_OPENID[CREATE] permission on the specified application " +
                    "or APPLICATION_OPENID[CREATE] permission on the specified domain " +
                    "or APPLICATION_OPENID[CREATE] permission on the specified environment " +
                    "or APPLICATION_OPENID[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application secret successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ClientSecret.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Valid @NotNull final NewClientSecret clientSecret,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION_OPENID, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(existingDomain -> applicationService.findById(application)
                                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                                .flatMapSingle(existingApplication -> applicationSecretService.create(existingDomain, existingApplication, clientSecret, authenticatedUser))
                                .map(secret -> Response.status(Response.Status.CREATED)
                                        .entity(secret)
                                        .build())))
                .subscribe(response::resume, response::resume);
    }

    @Path("{secret}")
    public ApplicationSecretResource getSecretResource() {
        return resourceContext.getResource(ApplicationSecretResource.class);
    }

}
