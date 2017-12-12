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
import io.gravitee.am.gateway.service.exception.DomainAlreadyExistsException;
import io.gravitee.am.gateway.service.model.NewScope;
import io.gravitee.am.model.ClientListItem;
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
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain", "oauth2"})
public class ScopesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List scopes for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List scopes for a security domain",
                    response = ClientListItem.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<Scope> listScopes(@PathParam("domain") String _domain) {
        return scopeService.findByDomain(_domain)
                .stream()
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getKey(), o2.getKey()))
                .collect(Collectors.toList());
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a scope")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Scope successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response createClient(
            @PathParam("domain") String domain,
            @ApiParam(name = "scope", required = true)
            @Valid @NotNull final NewScope newScope) throws DomainAlreadyExistsException {
        domainService.findById(domain);

        io.gravitee.am.model.oauth2.Scope scope = scopeService.create(domain, newScope);
        if (scope != null) {
            return Response
                    .created(URI.create("/domains/" + domain + "/scopes/" + scope.getId()))
                    .entity(scope)
                    .build();
        }

        return Response.serverError().build();
    }

    @Path("{scope}")
    public ScopeResource getScopeResource() {
        return resourceContext.getResource(ScopeResource.class);
    }
}
