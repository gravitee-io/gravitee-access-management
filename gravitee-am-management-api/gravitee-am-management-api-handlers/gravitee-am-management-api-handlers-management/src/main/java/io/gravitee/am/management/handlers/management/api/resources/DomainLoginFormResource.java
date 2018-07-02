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

import io.gravitee.am.model.login.LoginForm;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateLoginForm;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainLoginFormResource {

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get custom login form for the security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Custom login form", response = LoginForm.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void getDomainLoginForm(@PathParam("domain") String domainId, @Suspended final AsyncResponse response) throws DomainNotFoundException {
        domainService.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .map(domain -> {
                    if (domain.getLoginForm() == null) {
                        return Response.status(HttpStatusCode.NOT_FOUND_404).build();
                    } else {
                        return Response.ok(domain.getLoginForm()).build();
                    }
                })
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Set custom login form for the security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Custom login form successfully updated", response = LoginForm.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void updateDomainLoginForm(
            @PathParam("domain") String domain,
            @ApiParam(name = "loginForm", required = true) @Valid @NotNull final UpdateLoginForm loginForm,
            @Suspended final AsyncResponse response) {
        domainService.updateLoginForm(domain, loginForm)
                .subscribe(
                        result -> response.resume(Response.ok(result).build()),
                        error -> response.resume(error));
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Delete custom login form for the security domain")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Custom login form successfully deleted", response = LoginForm.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void deleteDomainLoginForm(
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {
        domainService.deleteLoginForm(domain)
            .subscribe(
                    success -> response.resume(Response.noContent().build()),
                    error -> response.resume(error));
    }
}
