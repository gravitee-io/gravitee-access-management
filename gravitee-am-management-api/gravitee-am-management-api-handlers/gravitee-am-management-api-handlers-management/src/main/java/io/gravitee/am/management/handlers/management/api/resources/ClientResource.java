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
import io.gravitee.am.model.Client;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.PatchClient;
import io.gravitee.am.service.utils.ResponseTypeUtils;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Context
    private ResourceContext resourceContext;

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

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Patch a client")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Client successfully patched", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void patch(
            @PathParam("domain") String domain,
            @PathParam("client") String client,
            @ApiParam(name = "client", required = true) @Valid @NotNull PatchClient patchClient,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> clientService.patch(domain, client, patchClient))
                .map(client1 -> Response.ok(client1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update (apply a patch) client")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Client successfully updated", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("domain") String domain,
            @PathParam("client") String client,
            @ApiParam(name = "client", required = true) @Valid @NotNull PatchClient patchClient,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> this.applyDefaultResponseType(patchClient))
                .flatMap(patch -> clientService.patch(domain, client, patch, true, authenticatedUser))
                .map(updatedClient -> Response.ok(updatedClient).build())
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
        final User authenticatedUser = getAuthenticatedUser();

        clientService.delete(client, authenticatedUser)
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }

    @POST
    @Path("secret/_renew")
    @ApiOperation(value = "Renew client secret")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses({
            @ApiResponse(code = 200, message = "Client secret successfully updated", response = Client.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void renewClientSecret(@PathParam("domain") String domain,
                            @PathParam("client") String client,
                            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(__ -> clientService.renewClientSecret(domain, client, authenticatedUser))
                .map(updatedClient -> Response.ok(updatedClient).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("emails")
    public ClientEmailsResource getEmailsResource() {
        return resourceContext.getResource(ClientEmailsResource.class);
    }

    @Path("forms")
    public ClientFormsResource getFormsResource() {
        return resourceContext.getResource(ClientFormsResource.class);
    }

    /**
     * Before dynamic client registration feature, response_type field was not managed.
     * In order to protect those who were using the PUT API without this new field, we'll add default value.
     * Only if the authorized grant types are informed and not the response_type.
     */
    private Single<PatchClient> applyDefaultResponseType(PatchClient patch) {
        if(patch.getAuthorizedGrantTypes()!=null && patch.getAuthorizedGrantTypes().isPresent() && patch.getResponseTypes()==null) {
            Set<String> responseTypes = ResponseTypeUtils.applyDefaultResponseType(patch.getAuthorizedGrantTypes().get());
            patch.setResponseTypes(Optional.of(responseTypes.stream().collect(Collectors.toList())));
        }
        return Single.just(patch);
    }
}
