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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewEmail;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"email"})
public class ApplicationEmailsResource extends AbstractResource {

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ApplicationService applicationService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Find a email for an application",
            notes = "User must have APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified application " +
                    "or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified domain " +
                    "or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified environment " +
                    "or APPLICATION_EMAIL_TEMPLATE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Email successfully fetched"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @NotNull @QueryParam("template") Template emailTemplate,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.READ)
                .andThen(emailTemplateService.findByDomainAndClientAndTemplate(domain, application, emailTemplate.template())
                        .map(email -> Response.ok(email).build())
                        .defaultIfEmpty(Response.ok(new Email(false, emailTemplate.template())).build()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a email for an application",
            notes = "User must have APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified application " +
                    "or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified domain " +
                    "or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified environment " +
                    "or APPLICATION_EMAIL_TEMPLATE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Email successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @ApiParam(name = "email", required = true)
            @Valid @NotNull final NewEmail newEmail,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> applicationService.findById(application))
                        .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                        .flatMapSingle(__ -> emailTemplateService.create(domain, application, newEmail, authenticatedUser))
                        .map(email -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/applications/" + application + "/emails/" + email.getId()))
                                .entity(email)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{email}")
    public ApplicationEmailResource getEmailResource() {
        return resourceContext.getResource(ApplicationEmailResource.class);
    }
}
