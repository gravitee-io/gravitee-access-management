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
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.TopClient;
import io.gravitee.am.service.model.TotalClient;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"dashboard"})
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
    public void listClients(@QueryParam("page") @DefaultValue("0") int page,
                            @QueryParam("size") @DefaultValue("10") int size,
                            @QueryParam("domainId") String domainId,
                            @QueryParam("q") String query,
                            @Suspended final AsyncResponse response) {
        int selectedSize = Math.min(size, MAX_CLIENTS_FOR_DASHBOARD);
        Single<AbstractMap.SimpleEntry<Page<Client>, Map<String, Domain>>> singleDashboardClients;

        if (domainId != null) {
            singleDashboardClients = domainService.findById(domainId)
                    .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                    .flatMapSingle(domain -> {
                        Map<String, Domain> domains = new HashMap<>();
                        domains.put(domainId, domain);
                        Single<Page<Client>> findClients;
                        if (query != null) {
                            findClients = clientService.search(domainId, query, page, size);
                        } else {
                            findClients = clientService.findByDomain(domainId, page, selectedSize);
                        }
                        return findClients
                                .map(pagedClients -> new AbstractMap.SimpleEntry<>(pagedClients, domains));
                    });
        } else {
            Single<Page<Client>> findClients;
            if (query != null) {
                findClients = clientService.searchAll(query, page, size);
            } else {
                findClients = clientService.findAll(page, selectedSize);
            }
            singleDashboardClients = findClients
                   .flatMap(pagedClients -> {
                       Set<String> domainIds = pagedClients.getData().stream().map(c -> c.getDomain()).collect(Collectors.toSet());
                       return domainService.findByIdIn(domainIds)
                               .map(domains -> domains.stream().collect(Collectors.toMap(Domain::getId, Function.identity())))
                               .map(domainsMap -> new AbstractMap.SimpleEntry<>(pagedClients, domainsMap));
                   });
        }

        singleDashboardClients
                .map(entry -> {
                    Page<Client> pagedClients = entry.getKey();
                    List<ClientListItem> sortedClients = pagedClients.getData()
                            .stream()
                            .map(clientEnhancer.enhanceClient(entry.getValue()))
                            .sorted((c1, c2) -> c2.getUpdatedAt().compareTo(c1.getUpdatedAt()))
                            .collect(Collectors.toList());
                    return new Page(sortedClients, pagedClients.getCurrentPage(), pagedClients.getTotalCount());
                })
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error)
                );
    }

    @Path("top")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List top clients by access tokens count")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List top clients by access tokens count",
                    response = TopClient.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void listTopClients(@QueryParam("size") @DefaultValue("10") int size,
                               @QueryParam("domainId") String domainId,
                               @Suspended final AsyncResponse response) {
        int selectedSize = Math.min(size, MAX_CLIENTS_FOR_DASHBOARD);
        Single<AbstractMap.SimpleEntry<Set<TopClient>, Map<String, Domain>>> singleDashboardTopClients;
        if (domainId != null) {
            singleDashboardTopClients = domainService.findById(domainId)
                    .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                    .flatMapSingle(domain -> {
                        Map<String, Domain> domains = new HashMap<>();
                        domains.put(domainId, domain);
                        return clientService.findTopClientsByDomain(domainId)
                                .map(topClients -> new AbstractMap.SimpleEntry<>(topClients, domains));
                    });
        } else {
            singleDashboardTopClients = clientService.findTopClients()
                    .flatMap(topClients -> {
                        Set<String> domainIds = topClients.stream().map(c -> c.getClient().getDomain()).collect(Collectors.toSet());
                        return domainService.findByIdIn(domainIds)
                                .map(domains -> domains.stream().collect(Collectors.toMap(Domain::getId, Function.identity())))
                                .map(domainsMap -> new AbstractMap.SimpleEntry<>(topClients, domainsMap));
                    });
        }

        singleDashboardTopClients
                .map(entry -> entry.getKey()
                        .stream()
                        .map(clientEnhancer.enhanceTopClient(entry.getValue()))
                        .sorted((c1, c2) -> Long.compare(c2.getAccessTokens(), c1.getAccessTokens()))
                        .limit(selectedSize)
                        .collect(Collectors.toList()))
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error)
                );
    }

    @Path("total")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List clients count")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List clients count",
                    response = TotalClient.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void listTotalClients(@QueryParam("domainId") String domainId,
                                 @Suspended final AsyncResponse response) {
        Single<TotalClient> totalClientSingle;
        if (domainId != null) {
            totalClientSingle = clientService.findTotalClientsByDomain(domainId);
        } else {
            totalClientSingle = clientService.findTotalClients();
        }
        totalClientSingle.subscribe(
                result -> response.resume(result),
                error -> response.resume(error));
    }

}
