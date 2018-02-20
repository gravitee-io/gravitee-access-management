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
package io.gravitee.am.management.handlers.management.api.resources.dashboard;

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.enhancer.ClientEnhancer;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.ClientListItem;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TopClientListItem;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.model.TopClient;
import io.gravitee.am.service.model.TotalClient;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"dashboard", "clients"})
public class DashboardClientsResource extends AbstractResource {

    private static final int MAX_CLIENTS_FOR_DASHBOARD = 25;

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ClientEnhancer clientEnhancer;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List last updated clients")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List last updated clients",
                    response = ClientListItem.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<ClientListItem> listClients(@QueryParam("page") @DefaultValue("0") int page,
                                            @QueryParam("size") @DefaultValue("10") int size,
                                            @QueryParam("domainId") String domainId) {

        int selectedSize = Math.min(size, MAX_CLIENTS_FOR_DASHBOARD);

        Page<Client> pagedClients;
        Map<String, Domain> domains = new HashMap<>();
        if (domainId != null) {
            Domain domain = domainService.findById(domainId);
            pagedClients = clientService.findByDomain(domainId, page, selectedSize);
            domains.put(domainId, domain);
        } else {
            pagedClients = clientService.findAll(page, selectedSize);
            List<String> domainIds = pagedClients.getData().stream().map(c -> c.getDomain()).collect(Collectors.toList());
            domains.putAll(domainService.findByIdIn(domainIds).stream().collect(Collectors.toMap(Domain::getId, Function.identity())));
        }

        return pagedClients.getData()
                .stream()
                .map(clientEnhancer.enhanceClient(domains))
                .sorted((c1, c2) -> c2.getUpdatedAt().compareTo(c1.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    @Path("top")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List top clients by access tokens count")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List top clients by access tokens count",
                    response = TopClient.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<TopClientListItem> listTopClients(@QueryParam("size") @DefaultValue("10") int size,
                                                  @QueryParam("domainId") String domainId) {

        int selectedSize = Math.min(size, MAX_CLIENTS_FOR_DASHBOARD);

        Set<TopClient> clients;
        Map<String, Domain> domains = new HashMap<>();
        if (domainId != null) {
            Domain domain = domainService.findById(domainId);
            clients = clientService.findTopClientsByDomain(domainId);
            domains.put(domainId, domain);
        } else {
            clients = clientService.findTopClients();
            List<String> domainIds = clients.stream().map(c -> c.getClient().getDomain()).collect(Collectors.toList());
            domains.putAll(domainService.findByIdIn(domainIds).stream().collect(Collectors.toMap(Domain::getId, Function.identity())));
        }

        return clients
                .stream()
                .map(clientEnhancer.enhanceTopClient(domains))
                .sorted((c1, c2) -> Long.compare(c2.getAccessTokens(), c1.getAccessTokens()))
                .limit(selectedSize)
                .collect(Collectors.toList());
    }

    @Path("total")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List clients count")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List clients count",
                    response = TotalClient.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public TotalClient listTotalClients(@QueryParam("domainId") String domainId) {

        TotalClient totalClient;
        if (domainId != null) {
            totalClient = clientService.findTotalClientsByDomain(domainId);
        } else {
            totalClient = clientService.findTotalClients();
        }

        return totalClient;
    }

}
