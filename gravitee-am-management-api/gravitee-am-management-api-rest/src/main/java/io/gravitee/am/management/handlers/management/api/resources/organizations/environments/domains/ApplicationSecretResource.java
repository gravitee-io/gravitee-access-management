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
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

public class ApplicationSecretResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationSecretService applicationSecretService;

    @POST
    @Path("_renew")
    @Operation(
            operationId = "renewClientSecret",
            summary = "Renew application secret",
            description = "User must have APPLICATION_OPENID[UPDATE] permission on the specified application " +
                    "or APPLICATION_OPENID[UPDATE] permission on the specified domain " +
                    "or APPLICATION_OPENID[UPDATE] permission on the specified environment " +
                    "or APPLICATION_OPENID[UPDATE] permission on the specified organization")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application secret successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ClientSecret.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void renewClientSecret(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @PathParam("secret") String secret,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION_OPENID, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(exitingDomain -> applicationService.findById(application)
                                .switchIfEmpty(Single.error(new ApplicationNotFoundException(application)))
                                .flatMap(application1 -> applicationSecretService.renew(exitingDomain, application1, secret, authenticatedUser)
                                        .map(app -> app.getSecrets().stream().filter(cs -> cs.getId().equals(secret)).findFirst().orElse(new ClientSecret())))))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "deleteClientSecret",
            summary = "Delete a secret of an application",
            description = "User must have APPLICATION_OPENID[DELETE] permission on the specified application " +
                    "or APPLICATION_OPENID[DELETE] permission on the specified domain " +
                    "or APPLICATION_OPENID[DELETE] permission on the specified environment " +
                    "or APPLICATION_OPENID[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Secret successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @PathParam("secret") String secret,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION_FORM, Acl.DELETE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(existingDomain ->
                                applicationService.findById(application)
                                        .switchIfEmpty(Single.error(new ApplicationNotFoundException(application)))
                                        .flatMap(app -> applicationSecretService.delete(existingDomain, app, secret, authenticatedUser)
                                                .toSingleDefault(app)
                                        )
                        )
                )
                .ignoreElement()
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        response::resume);
    }
}
