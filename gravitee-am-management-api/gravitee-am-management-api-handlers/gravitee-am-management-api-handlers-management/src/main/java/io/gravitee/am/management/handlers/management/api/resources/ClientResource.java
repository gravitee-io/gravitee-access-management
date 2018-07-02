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

import io.gravitee.am.model.Client;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateClient;
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
public class ClientResource extends AbstractResource {

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a client")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Client", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("domain") String domain,
            @PathParam("client") String client,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(irrelevant -> clientService.findById(client))
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(client)))
                .map(client1 -> {
                    if (!client1.getDomain().equalsIgnoreCase(domain)) {
                        throw new BadRequestException("Client does not belong to domain");
                    }
                    return Response.ok(client1).build();
                })
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a client")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Client successfully updated", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("domain") String domain,
            @PathParam("client") String client,
            @ApiParam(name = "client", required = true) @Valid @NotNull UpdateClient updateClient,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> clientService.update(domain, client, updateClient))
                .map(client1 -> Response.ok(client1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Delete a client")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Client successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(@PathParam("domain") String domain,
                       @PathParam("client") String client,
                       @Suspended final AsyncResponse response) {
        clientService.delete(client)
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }
}
