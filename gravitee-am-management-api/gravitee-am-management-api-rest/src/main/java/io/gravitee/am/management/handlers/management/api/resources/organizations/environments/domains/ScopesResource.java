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

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.ClientListItem;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewScope;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.*;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = { "scope" })
public class ScopesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
        value = "List scopes for a security domain",
        notes = "User must have the DOMAIN_SCOPE[LIST] permission on the specified domain " +
        "or DOMAIN_SCOPE[LIST] permission on the specified environment " +
        "or DOMAIN_SCOPE[LIST] permission on the specified organization " +
        "Each returned scope is filtered and contains only basic information such as id, key, name, description, isSystem and isDiscovery."
    )
    @ApiResponses(
        {
            @ApiResponse(
                code = 200,
                message = "List scopes for a security domain",
                response = ClientListItem.class,
                responseContainer = "Set"
            ),
            @ApiResponse(code = 500, message = "Internal server error"),
        }
    )
    public void list(
        @PathParam("organizationId") String organizationId,
        @PathParam("environmentId") String environmentId,
        @PathParam("domain") String domain,
        @Suspended final AsyncResponse response
    ) {
        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_SCOPE, Acl.LIST)
            .andThen(
                scopeService
                    .findByDomain(domain)
                    .map(
                        scopes -> {
                            List<Scope> sortedScopes = scopes
                                .stream()
                                .map(this::filterScopeInfos)
                                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getKey(), o2.getKey()))
                                .collect(Collectors.toList());
                            return Response.ok(sortedScopes).build();
                        }
                    )
            )
            .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
        value = "Create a scope",
        notes = "User must have the DOMAIN_SCOPE[CREATE] permission on the specified domain " +
        "or DOMAIN_SCOPE[CREATE] permission on the specified environment " +
        "or DOMAIN_SCOPE[CREATE] permission on the specified organization"
    )
    @ApiResponses(
        { @ApiResponse(code = 201, message = "Scope successfully created"), @ApiResponse(code = 500, message = "Internal server error") }
    )
    public void create(
        @PathParam("organizationId") String organizationId,
        @PathParam("environmentId") String environmentId,
        @PathParam("domain") String domain,
        @ApiParam(name = "scope", required = true) @Valid @NotNull final NewScope newScope,
        @Suspended final AsyncResponse response
    ) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_SCOPE, Acl.CREATE)
            .andThen(
                domainService
                    .findById(domain)
                    .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                    .flatMapSingle(
                        irrelevant ->
                            scopeService
                                .create(domain, newScope, authenticatedUser)
                                .map(
                                    scope ->
                                        Response
                                            .created(
                                                URI.create(
                                                    "/organizations/" +
                                                    organizationId +
                                                    "/environments/" +
                                                    environmentId +
                                                    "/domains/" +
                                                    domain +
                                                    "/scopes/" +
                                                    scope.getId()
                                                )
                                            )
                                            .entity(scope)
                                            .build()
                                )
                    )
            )
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
        filteredScope.setDescription(scope.getDescription());

        return filteredScope;
    }
}
