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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.ClientListItem;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.enhancer.ClientEnhancer;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewClient;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"client"})
@Deprecated
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
    @ApiOperation(value = "List registered clients for a security domain",
            notes = "User must have the APPLICATION[LIST] permission on the specified domain " +
                    "or APPLICATION[LIST] permission on the specified environment " +
                    "or APPLICATION[LIST] permission on the specified organization " +
                    "AND either APPLICATION[READ] permission on each domain's application " +
                    "or APPLICATION[READ] permission on the specified domain " +
                    "or APPLICATION[READ] permission on the specified environment " +
                    "or APPLICATION[READ] permission on the specified organization).")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered clients for a security domain",
                    response = ClientListItem.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @QueryParam("q") String query,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        int requestedPage = page == null ? 0 : page;
        int requestedSize = size == null ? 100 : Math.min(100, size);

        checkAnyPermission(organizationId, environmentId, domainId, Permission.APPLICATION, Acl.LIST)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(domain -> getClients(domainId, query, 0, Integer.MAX_VALUE)
                                .flatMap(pagedClients ->
                                        Maybe.concat(pagedClients.getData().stream()
                                                .map(client -> hasAnyPermission(authenticatedUser, organizationId, environmentId, domainId, client.getId(), Permission.APPLICATION, Acl.READ)
                                                        .filter(Boolean::booleanValue)
                                                        .map(permit -> client)).collect(Collectors.toList()))
                                                .toList()
                                                .map(clients -> {
                                                    List<ClientListItem> sortedClients = clients.stream().map(clientEnhancer.enhanceClient(Collections.singletonMap(domainId, domain)))
                                                            .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getClientId(), o2.getClientId()))
                                                            .collect(Collectors.toList());

                                                    if (page == null || size == null) {
                                                        return sortedClients;
                                                    }
                                                    return new Page<>(sortedClients.stream().skip(page * size).limit(size).collect(Collectors.toList()), pagedClients.getCurrentPage(), sortedClients.size());
                                                }))))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a client",
            notes = "User must have APPLICATION[CREATE] permission on the specified domain " +
                    "or APPLICATION[CREATE] permission on the specified environment " +
                    "or APPLICATION[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Client successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void createClient(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @ApiParam(name = "client", required = true)
            @Valid @NotNull final NewClient newClient,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> clientService.create(domain, newClient, authenticatedUser)
                                .map(client -> Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/clients/" + client.getId()))
                                        .entity(client)
                                        .build())
                        ))
                .subscribe(response::resume, response::resume);
    }

    @Path("{client}")
    public ClientResource getClientResource() {
        return resourceContext.getResource(ClientResource.class);
    }

    private Single<Page<Client>> getClients(String domainId, String query, int page, int size) {
        if (query != null) {
            return clientService.search(domainId, query, page, size);
        } else {
            return clientService.findByDomain(domainId, page, size);
        }
    }
}
