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

import io.gravitee.am.management.handlers.management.api.model.ThemeEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ThemeService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewTheme;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
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
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"theme"})
public class ThemesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ThemeService themeService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "listThemes",
            value = "List themes on the specified security domain",
            notes = "User must have the DOMAIN_THEME[LIST] permission on the specified domain " +
                    "or DOMAIN_THEME[LIST] permission on the specified environment " +
                    "or DOMAIN_THEME[LIST] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List of themes", response = ThemeEntity.class, responseContainer = "List"),
            @ApiResponse(code = 204, message = "There is no themes on this domain"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            // TODO do we have to manage Page or a simple list is enough ?
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_THEME, Acl.LIST)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMap(domain -> this.themeService.findByReference(ReferenceType.DOMAIN, domainId).map(ThemeEntity::new))
                .map(List::of)
                .switchIfEmpty(Maybe.just(Collections.emptyList()))
                .subscribe(themes -> {
                    if (themes == null || themes.isEmpty()) {
                        response.resume(Response.noContent().build());
                    } else {
                        response.resume(themes);
                    }
                }, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "createTheme",
            value = "Create a theme on the specified security domain",
            notes = "User must have the DOMAIN_THEME[CREATE] permission on the specified domain " +
                    "or DOMAIN_THEME[CREATE] permission on the specified environment " +
                    "or DOMAIN_THEME[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Theme successfully created", response = ThemeEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @ApiParam(name = "theme", required = true)
            @Valid @NotNull final NewTheme newTheme,
            @Suspended final AsyncResponse response) {

        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_THEME, Acl.CREATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapSingle(domain -> themeService.create(domain, newTheme, authenticatedUser))
                .map(theme -> Response
                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domainId + "/themes/" + theme.getId()))
                        .entity(new ThemeEntity(theme))
                        .build())
                .subscribe(response::resume, response::resume);
    }

    @Path("{themeId}")
    public ThemeResource getThemeResource() {
        return resourceContext.getResource(ThemeResource.class);
    }

}
