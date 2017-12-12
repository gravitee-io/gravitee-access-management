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
package io.gravitee.am.gateway.handler.management.api.resources;

import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.ScopeService;
import io.gravitee.am.gateway.service.exception.DomainNotFoundException;
import io.gravitee.am.gateway.service.model.UpdateScope;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain", "oauth2"})
public class ScopeResource extends AbstractResource {

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private DomainService domainService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a scope")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Client", response = Scope.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response get(
            @PathParam("domain") String domain,
            @PathParam("scope") String scopeId) throws DomainNotFoundException {
        domainService.findById(domain);

        Scope scope = scopeService.findById(scopeId);
        if (! scope.getDomain().equalsIgnoreCase(domain)) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Client does not belong to domain")
                    .build();
        }
        return Response.ok(scope).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a scope")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Scope successfully updated", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Scope updateClient(
            @PathParam("domain") String domain,
            @PathParam("scope") String scope,
            @ApiParam(name = "scope", required = true) @Valid @NotNull UpdateScope updateScope) {
        domainService.findById(domain);

        return scopeService.update(domain, scope, updateScope);
    }

    @DELETE
    @ApiOperation(value = "Delete a scope")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Scope successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response delete(@PathParam("domain") String domain, @PathParam("scope") String scope) {
        scopeService.delete(scope);

        return Response.noContent().build();
    }
}
