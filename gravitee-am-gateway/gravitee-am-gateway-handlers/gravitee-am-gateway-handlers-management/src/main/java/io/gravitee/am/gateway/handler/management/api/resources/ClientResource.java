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

import io.gravitee.am.gateway.service.ClientService;
import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.exception.DomainNotFoundException;
import io.gravitee.am.gateway.service.model.UpdateClient;
import io.gravitee.am.model.Client;
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
public class ClientResource extends AbstractResource {

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a client")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Client", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response get(
            @PathParam("domain") String domain,
            @PathParam("client") String client) throws DomainNotFoundException {
        domainService.findById(domain);

        Client client1 = clientService.findById(client);
        if (! client1.getDomain().equalsIgnoreCase(domain)) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Client does not belong to domain")
                    .build();
        }
        return Response.ok(client1).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a client")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Client successfully updated", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Client updateClient(
            @PathParam("domain") String domain,
            @PathParam("client") String client,
            @ApiParam(name = "client", required = true) @Valid @NotNull UpdateClient updateClient) {
        domainService.findById(domain);

        return clientService.update(domain, client, updateClient);
    }

    @DELETE
    @ApiOperation(value = "Delete a client")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Client successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response delete(@PathParam("domain") String domain, @PathParam("client") String client) {
        clientService.delete(client);

        return Response.noContent().build();
    }

    @GET
    @Path("identities")
    public ClientIdentityProvidersResource getClientIdentityProvidersResource() {
        return resourceContext.getResource(ClientIdentityProvidersResource.class);
    }
}
