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

import io.gravitee.am.model.Email;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.EmailTemplateService;
import io.gravitee.am.service.exception.ClientNotFoundException;
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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"email"})
public class ClientEmailResource extends AbstractResource {

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ClientService clientService;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update an email for a client")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Email successfully updated", response = Email.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("domain") String domain,
            @PathParam("client") String client,
            @PathParam("email") String email,
            @ApiParam(name = "email", required = true) @Valid @NotNull UpdateEmail updateEmail,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(irrelevant -> clientService.findById(client))
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(client)))
                .flatMapSingle(__ -> emailTemplateService.update(domain, client, email, updateEmail))
                .map(email1 -> Response.ok(email1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Delete an email for a client")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Email successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(@PathParam("domain") String domain,
                       @PathParam("email") String email,
                       @Suspended final AsyncResponse response) {
        emailTemplateService.delete(email)
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }

}
