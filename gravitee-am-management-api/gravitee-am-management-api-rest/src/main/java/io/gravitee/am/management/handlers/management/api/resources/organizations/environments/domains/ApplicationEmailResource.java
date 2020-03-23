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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateEmail;
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

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"email"})
public class ApplicationEmailResource extends AbstractResource {

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EmailManager emailManager;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update an email for an application",
            notes = "User must have APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified application " +
                    "or APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified domain " +
                    "or APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified environment " +
                    "or APPLICATION_EMAIL_TEMPLATE[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Email successfully updated", response = Email.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @PathParam("email") String email,
            @ApiParam(name = "email", required = true) @Valid @NotNull UpdateEmail updateEmail,
            @Suspended final AsyncResponse response) {

        checkPermissions(or(of(ReferenceType.APPLICATION, application, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.UPDATE),
                of(ReferenceType.DOMAIN, domain, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.UPDATE),
                of(ReferenceType.ENVIRONMENT, environmentId, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.UPDATE),
                of(ReferenceType.ORGANIZATION, organizationId, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.UPDATE)))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(irrelevant -> applicationService.findById(application))
                        .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                        .flatMapSingle(irrelevant -> emailTemplateService.update(domain, application, email, updateEmail))
                        .flatMap(email1 -> emailManager.reloadEmail(email1)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete an email for an application",
            notes = "User must have APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified application " +
                    "or APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified domain " +
                    "or APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified environment " +
                    "or APPLICATION_EMAIL_TEMPLATE[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Email successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @PathParam("email") String email,
            @Suspended final AsyncResponse response) {

        checkPermissions(or(of(ReferenceType.APPLICATION, application, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.DELETE),
                of(ReferenceType.DOMAIN, domain, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.DELETE),
                of(ReferenceType.ENVIRONMENT, environmentId, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.DELETE),
                of(ReferenceType.ORGANIZATION, organizationId, Permission.APPLICATION_EMAIL_TEMPLATE, Acl.DELETE)))
                .andThen(emailTemplateService.delete(email)
                        .andThen(emailManager.deleteEmail(email)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}