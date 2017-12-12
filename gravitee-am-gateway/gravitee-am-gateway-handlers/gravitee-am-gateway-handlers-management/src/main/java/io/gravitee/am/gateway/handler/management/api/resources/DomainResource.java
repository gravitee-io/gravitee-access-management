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
import io.gravitee.am.gateway.service.exception.DomainNotFoundException;
import io.gravitee.am.gateway.service.model.UpdateDomain;
import io.gravitee.am.model.Domain;
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
@Api(tags = {"domain"})
public class DomainResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Domain", response = Domain.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Domain get(@PathParam("domain") String domain) throws DomainNotFoundException {
        return domainService.findById(domain);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update the security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Domain successfully updated", response = Domain.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Domain update(
            @ApiParam(name = "domain", required = true) @Valid @NotNull final UpdateDomain domainToUpdate,
            @PathParam("domain") String domain) {
        return domainService.update(domain, domainToUpdate);
    }

    @DELETE
    @ApiOperation(value = "Delete the security domain")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Domain successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response delete(@PathParam("domain") String domain) {
        domainService.delete(domain);

        return Response.noContent().build();
    }

    @Path("clients")
    public ClientsResource getClientsResource() {
        return resourceContext.getResource(ClientsResource.class);
    }

    @Path("identities")
    public IdentityProvidersResource getIdentityProvidersResource() {
        return resourceContext.getResource(IdentityProvidersResource.class);
    }

    @Path("certificates")
    public CertificatesResource getCertificatesResource() {
        return resourceContext.getResource(CertificatesResource.class);
    }

    @Path("roles")
    public RolesResource getRolesResource() {
        return resourceContext.getResource(RolesResource.class);
    }

    @Path("login")
    public DomainLoginFormResource getDomainLoginFormResource() {
        return resourceContext.getResource(DomainLoginFormResource.class);
    }

    @Path("users")
    public UsersResource getUsersResource() {
        return resourceContext.getResource(UsersResource.class);
    }

    @Path("extensionGrants")
    public ExtensionGrantsResource getTokenGrantersResource() {
        return resourceContext.getResource(ExtensionGrantsResource.class);
    }

    @Path("scopes")
    public ScopesResource getScopesResource() {
        return resourceContext.getResource(ScopesResource.class);
    }
}
