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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.FormService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateForm;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"form"})
public class ApplicationFormResource extends AbstractResource {

    @Autowired
    private FormService formService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ApplicationService applicationService;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a form for an application")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Form successfully updated", response = Form.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.APPLICATION_FORM, acls = RolePermissionAction.UPDATE)
    })
    public void update(
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @PathParam("form") String form,
            @ApiParam(name = "form", required = true) @Valid @NotNull UpdateForm updateForm,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(irrelevant -> applicationService.findById(application))
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                .flatMapSingle(irrelevant -> formService.update(domain, application, form, updateForm, authenticatedUser))
                .map(email1 -> Response.ok(email1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Delete a form for an application")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Form successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.APPLICATION_FORM, acls = RolePermissionAction.DELETE)
    })
    public void delete(@PathParam("domain") String domain,
                       @PathParam("form") String form,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        formService.delete(domain, form, authenticatedUser)
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }

}
