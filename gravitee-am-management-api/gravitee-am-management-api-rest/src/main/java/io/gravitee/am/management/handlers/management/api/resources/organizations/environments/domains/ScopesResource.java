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
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewScope;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.swagger.annotations.*;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"scope"})
public class ScopesResource extends AbstractResource {
    private static final int MAX_SCOPES_SIZE_PER_PAGE = 50;
    private static final String MAX_SCOPES_SIZE_PER_PAGE_STRING = "50";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "listScopes",
            value = "List scopes for a security domain",
            notes = "User must have the DOMAIN_SCOPE[LIST] permission on the specified domain " +
                    "or DOMAIN_SCOPE[LIST] permission on the specified environment " +
                    "or DOMAIN_SCOPE[LIST] permission on the specified organization " +
                    "Each returned scope is filtered and contains only basic information such as id, key, name, description, isSystem and isDiscovery.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List scopes for a security domain", response = ScopePage.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_SCOPES_SIZE_PER_PAGE_STRING) int size,
            @QueryParam("q") String query,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_SCOPE, Acl.LIST)
                .andThen(query != null ? scopeService.search(domain, query, page, Math.min(size, MAX_SCOPES_SIZE_PER_PAGE)) : scopeService.findByDomain(domain, page, Math.min(size, MAX_SCOPES_SIZE_PER_PAGE))
                ).map(searchPage -> new ScopePage(
                        searchPage.getData().stream().map(this::filterScopeInfos).sorted(Comparator.comparing(Scope::getKey)).collect(toList()),
                        searchPage.getCurrentPage(),
                        searchPage.getTotalCount())
                )
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "createScope",
            value = "Create a scope",
            notes = "User must have the DOMAIN_SCOPE[CREATE] permission on the specified domain " +
                    "or DOMAIN_SCOPE[CREATE] permission on the specified environment " +
                    "or DOMAIN_SCOPE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Scope successfully created", response = Scope.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @ApiParam(name = "scope", required = true)
            @Valid @NotNull final NewScope newScope,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_SCOPE, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> scopeService.create(domain, newScope, authenticatedUser)
                                .map(scope -> Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/scopes/" + scope.getId()))
                                        .entity(scope)
                                        .build())
                        ))
                .subscribe(response::resume, response::resume);
    }

    @Path("{scope}")
    public ScopeResource getScopeResource() {
        return resourceContext.getResource(ScopeResource.class);
    }

    private Scope filterScopeInfos(Scope scope) {
        Scope filteredScope = new Scope();
        filteredScope.setId(scope.getId());
        filteredScope.setKey(scope.getKey());
        filteredScope.setName(scope.getName());
        filteredScope.setSystem(scope.isSystem());
        filteredScope.setDiscovery(scope.isDiscovery());
        filteredScope.setParameterized(scope.isParameterized());
        filteredScope.setDescription(scope.getDescription());

        return filteredScope;
    }

    public static class ScopePage extends Page<Scope> {

        public ScopePage(Collection<Scope> data, int currentPage, long totalCount) {
            super(data, currentPage, totalCount);
        }
    }
}
