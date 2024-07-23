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

import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import io.gravitee.am.management.handlers.management.api.model.ThemeEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.ThemeService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.ThemeNotFoundException;
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
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static java.util.Objects.isNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "theme")
public class ThemeResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ThemeService themeService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getTheme",
            summary = "Get the theme linked to the specified security domain",
            description = "User must have the DOMAIN_THEME[READ] permission on the specified domain " +
                    "or DOMAIN_THEME[READ] permission on the specified environment " +
                    "or DOMAIN_THEME[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Get theme description",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ThemeEntity.class))),
            @ApiResponse(responseCode = "404", description = "Theme doesn't exist"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void read(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("themeId") String themeId,
            @Suspended final AsyncResponse response) {
        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_THEME, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMap(domain -> themeService.getTheme(domain, themeId))
                .map(ThemeEntity::new)
                .switchIfEmpty(Maybe.error(new ThemeNotFoundException(themeId, domainId)))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateTheme",
            summary = "Update a theme on the specified security domain",
            description = "User must have the DOMAIN_THEME[UPDATE] permission on the specified domain " +
                    "or DOMAIN_THEME[UPDATE] permission on the specified environment " +
                    "or DOMAIN_THEME[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Theme successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ThemeEntity.class))),
            @ApiResponse(responseCode = "404", description = "Theme Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("themeId") String themeId,
            @Parameter(name = "theme", required = true)
            @Valid @NotNull final ThemeEntity updateTheme,
            @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        if (isNull(updateTheme.getId())) {
            updateTheme.setId(themeId);
        }
        if (isNull(updateTheme.getReferenceId())) {
            updateTheme.setReferenceId(domainId);
        }
        if (isNull(updateTheme.getReferenceType())) {
            updateTheme.setReferenceType(ReferenceType.DOMAIN);
        }

        if (!(themeId.equals(updateTheme.getId()) && domainId.equals(updateTheme.getReferenceId()) && ReferenceType.DOMAIN.equals(updateTheme.getReferenceType()))) {
            final ErrorEntity error = new ErrorEntity();
            error.setHttpCode(BAD_REQUEST.getStatusCode());
            error.setMessage("ThemeId or ReferenceId mismatch");
            response.resume(Response.status(BAD_REQUEST)
                    .type(APPLICATION_JSON_TYPE)
                    .entity(error).build());
            return;
        }

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_THEME, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapSingle(domain -> themeService.update(domain, updateTheme.asTheme(), authenticatedUser))
                .map(ThemeEntity::new)
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "deleteTheme",
            summary = "Delete a theme on the specified security domain",
            description = "User must have the DOMAIN_THEME[DELETE] permission on the specified domain " +
                    "or DOMAIN_THEME[DELETE] permission on the specified environment " +
                    "or DOMAIN_THEME[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Theme successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("themeId") String theme,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_THEME, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapCompletable(domain -> themeService.delete(domain, theme, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

}
