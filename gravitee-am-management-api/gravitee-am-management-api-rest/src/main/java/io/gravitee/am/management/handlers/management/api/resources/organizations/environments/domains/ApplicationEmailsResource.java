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
import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewEmail;
import io.gravitee.am.service.validators.email.resource.EmailTemplateValidator;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "email")
public class ApplicationEmailsResource extends AbstractResource {

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ApplicationService applicationService;

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private EmailTemplateValidator emailTemplateValidator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findApplicationEmail",
            summary = "Find a email for an application",
            description = "User must have APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified application " +
                    "or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified domain " +
                    "or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified environment " +
                    "or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email successfully fetched"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @NotNull @QueryParam("template") Template emailTemplate,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, ReferenceType.APPLICATION, application, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.READ)
                .andThen(emailTemplateService.findByDomainAndClientAndTemplate(domain, application, emailTemplate.template())
                        .map(email -> Response.ok(email).build())
                        .defaultIfEmpty(Response.ok(new Email(false, emailTemplate.template())).build()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createApplicationEmail",
            summary = "Create a email for an application",
            description = "User must have APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified application " +
                    "or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified domain " +
                    "or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified environment " +
                    "or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Email successfully created"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Parameter(name = "email", required = true)
            @Valid @NotNull final NewEmail newEmail,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.CREATE)
                .andThen(emailTemplateValidator.validate(newEmail))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(existingDomain -> applicationService.findById(application)
                                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                                .flatMapSingle(__ -> emailTemplateService.create(existingDomain, application, newEmail, authenticatedUser))
                                .map(email -> Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/applications/" + application + "/emails/" + email.getId()))
                                        .entity(email)
                                        .build())))
                .subscribe(response::resume, response::resume);
    }

    @Path("{email}")
    public ApplicationEmailResource getEmailResource() {
        return resourceContext.getResource(ApplicationEmailResource.class);
    }
}
