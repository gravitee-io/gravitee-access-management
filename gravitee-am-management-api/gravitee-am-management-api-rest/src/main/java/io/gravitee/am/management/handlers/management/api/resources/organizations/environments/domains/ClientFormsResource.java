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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewForm;
import io.gravitee.common.http.HttpStatusCode;
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

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"form"})
@Deprecated
public class ClientFormsResource extends AbstractResource {

    @Autowired
    private FormService formService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ClientService clientService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Find a form for a client",
            notes = "User must have APPLICATION_FORM[READ] permission on the specified client " +
                    "or APPLICATION_FORM[READ] permission on the specified domain " +
                    "or APPLICATION_FORM[READ] permission on the specified environment " +
                    "or APPLICATION_FORM[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Form successfully fetched"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("client") String client,
            @NotNull @QueryParam("template") Template emailTemplate,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, client, Permission.APPLICATION_FORM, Acl.READ)
                .andThen(formService.findByDomainAndClientAndTemplate(domain, client, emailTemplate.template())
                        .map(form -> Response.ok(form).build())
                        .defaultIfEmpty(Response.status(HttpStatusCode.NOT_FOUND_404).build()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a form for a client",
            notes = "User must have APPLICATION_FORM[READ] permission on the specified client " +
                    "or APPLICATION_FORM[READ] permission on the specified domain " +
                    "or APPLICATION_FORM[READ] permission on the specified environment " +
                    "or APPLICATION_FORM[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Form successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("client") String client,
            @ApiParam(name = "email", required = true)
            @Valid @NotNull final NewForm newForm,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, client, Permission.APPLICATION_FORM, Acl.DELETE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> clientService.findById(client))
                        .switchIfEmpty(Maybe.error(new ClientNotFoundException(client)))
                        .flatMapSingle(irrelevant -> formService.create(domain, client, newForm, authenticatedUser))
                        .map(form -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/clients/" + client + "/forms/" + form.getId()))
                                .entity(form)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{form}")
    public ClientFormResource getFormResource() {
        return resourceContext.getResource(ClientFormResource.class);
    }
}