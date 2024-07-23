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

import io.gravitee.am.management.handlers.management.api.model.PreviewRequest;
import io.gravitee.am.management.handlers.management.api.model.PreviewResponse;
import io.gravitee.am.management.handlers.management.api.preview.PreviewService;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.utils.RedirectUtils;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Locale;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "form"), @Tag(name= "preview")})
public class PreviewResource extends AbstractResource {

    @Autowired
    private Environment environment;

    @Autowired
    private DomainService domainService;

    @Autowired
    private PreviewService previewService;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Render the provided template",
            operationId = "renderDomainTemplate",
            description = "User must have the DOMAIN_THEME[READ] permission on the specified domain " +
                    "or DOMAIN_THEME[READ] permission on the specified environment " +
                    "or DOMAIN_THEME[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template successfully rendered",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PreviewResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void renderDomainTemplate(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Valid @NotNull final PreviewRequest request,
            @Context HttpHeaders headers,
            @Context HttpServletRequest httpRequest,
            @Suspended final AsyncResponse response) {

        final var locale = headers
                .getAcceptableLanguages()
                .stream()
                .filter(l -> !"*".equalsIgnoreCase(l.getLanguage()))
                .findFirst().orElse(Locale.ENGLISH);

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_THEME, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(domain -> this.previewService.previewDomainForm(domainId, request, locale, buildAssetsBaseUrl(httpRequest)))
                        .map(preview -> Response.ok(preview).build()))
                .subscribe(response::resume, response::resume);
    }

    private String buildAssetsBaseUrl(HttpServletRequest request) {
        final var builder = RedirectUtils.preBuildLocationHeader(request);
        // append context path
        builder.path(request.getContextPath() == null ? environment.getProperty("http.api.entrypoint", "/management") : request.getContextPath());

        return builder.build().toUriString();
    }
}
