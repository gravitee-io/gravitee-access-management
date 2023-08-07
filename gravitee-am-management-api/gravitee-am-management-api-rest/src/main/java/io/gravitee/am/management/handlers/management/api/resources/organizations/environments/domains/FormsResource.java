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
import io.gravitee.am.management.handlers.management.api.preview.PreviewService;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewForm;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "form")
public class FormsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private FormService formService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private PreviewService previewService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Find a form",
            description = "User must have the DOMAIN_FORM[READ] permission on the specified domain " +
                    "or DOMAIN_FORM[READ] permission on the specified environment " +
                    "or DOMAIN_FORM[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Form successfully fetched"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @NotNull @QueryParam("template") Template formTemplate,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_FORM, Acl.READ)
                .andThen(
                        formService.findByDomainAndTemplate(domain, formTemplate.template())
                                .switchIfEmpty(formService.getDefaultByDomainAndTemplate(domain, formTemplate.template()))
                                .map(form -> Response.ok(form).build())

                ).subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a form",
            description = "User must have the DOMAIN_FORM[CREATE] permission on the specified domain " +
                    "or DOMAIN_FORM[CREATE] permission on the specified environment " +
                    "or DOMAIN_FORM[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Form successfully created"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Parameter(name = "form", required = true)
            @Valid @NotNull final NewForm newForm,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_FORM, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> formService.create(domain, newForm, authenticatedUser)
                                .map(form -> Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/forms/" + form.getId()))
                                        .entity(form)
                                        .build())))
                .subscribe(response::resume, response::resume);
    }

    @Path("{form}")
    public FormResource getFormResource() {
        return resourceContext.getResource(FormResource.class);
    }
    @Path("preview")
    public PreviewResource getPreviewResource() {
        return resourceContext.getResource(PreviewResource.class);
    }
}
