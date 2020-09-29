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
package io.gravitee.am.management.handlers.management.api.resources.organizations.forms;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.model.NewForm;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
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
@Api(tags = {"form"})
public class FormsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private FormService formService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Find an organization form template",
            notes = "User must have the ORGANIZATION_FORM[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Form successfully fetched"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @NotNull @QueryParam("template") Template formTemplate,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_FORM, Acl.READ)
                .andThen(formService.findByTemplate(ReferenceType.ORGANIZATION, organizationId, formTemplate.template())
                        .map(page -> Response.ok(page).build())
                        .defaultIfEmpty(Response.ok(new Form(false, formTemplate.template())).build()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a form",
            notes = "User must have the ORGANIZATION_FORM[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Form successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @ApiParam(name = "form", required = true) @Valid @NotNull final NewForm newForm,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_FORM, Acl.CREATE)
                .andThen(formService.create(ReferenceType.ORGANIZATION, organizationId, newForm, authenticatedUser)
                        .map(form -> Response
                                .created(URI.create("/organizations/" + organizationId + "/forms/" + form.getId()))
                                .entity(form)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{form}")
    public FormResource getFormResource() {
        return resourceContext.getResource(FormResource.class);
    }
}
