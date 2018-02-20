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

import io.gravitee.am.management.handlers.management.api.resources.enhancer.ClientEnhancer;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.ClientListItem;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainAlreadyExistsException;
import io.gravitee.am.service.model.NewClient;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain", "oauth2"})
public class ClientsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ClientEnhancer clientEnhancer;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered clients for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered clients for a security domain",
                    response = ClientListItem.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<ClientListItem> listClients(@PathParam("domain") String _domain) {
        Domain domain = domainService.findById(_domain);

        return clientService.findByDomain(_domain)
                .stream()
                .map(clientEnhancer.enhanceClient(Collections.singletonMap(domain.getId(), domain)))
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getClientId(), o2.getClientId()))
                .collect(Collectors.toList());
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a client")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Client successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response createClient(
            @PathParam("domain") String domain,
            @ApiParam(name = "client", required = true)
            @Valid @NotNull final NewClient newClient) throws DomainAlreadyExistsException {
        domainService.findById(domain);

        Client client = clientService.create(domain, newClient);
        if (client != null) {
            return Response
                    .created(URI.create("/domains/" + domain + "/clients/" + client.getId()))
                    .entity(client)
                    .build();
        }

        return Response.serverError().build();
    }

    @Path("{client}")
    public ClientResource getClientResource() {
        return resourceContext.getResource(ClientResource.class);
    }
}
