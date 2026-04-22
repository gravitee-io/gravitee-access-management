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
import io.gravitee.am.management.handlers.management.api.resources.model.AgentApplication;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;

@Tag(name = "application")
public class AgentApplicationsResource extends AbstractDomainResource {

    private static final String MAX_AGENTS_PER_PAGE_STRING = "50";
    private static final int MAX_AGENTS_PER_PAGE = 100;

    @Autowired
    private ApplicationService applicationService;

    @GET
    @Produces(io.gravitee.common.http.MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listAgentApplications",
            summary = "List applications flagged as agent identities for a security domain",
            description = "User must have the APPLICATION[LIST] permission on the specified domain, environment or organization " +
                    "AND either APPLICATION[READ] permission on each domain's application " +
                    "or APPLICATION[READ] permission on the specified domain, environment or organization. " +
                    "Returns only applications with settings.advanced.agentIdentityMode = true.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "List agent applications for a security domain",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = AgentApplicationPage.class))
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_AGENTS_PER_PAGE_STRING) int size,
            @QueryParam("q") String query,
            @Suspended final AsyncResponse response) {
        final int clampedSize = Math.min(size, MAX_AGENTS_PER_PAGE);
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION, Acl.LIST)
                .andThen(checkDomainExists(domain).ignoreElement())
                .andThen(hasAnyPermission(authenticatedUser, organizationId, environmentId, domain, Permission.APPLICATION, Acl.READ)
                        .filter(hasPermission -> hasPermission)
                        .flatMapSingle(__ -> listAgents(domain, page, clampedSize, query))
                        .switchIfEmpty(
                                getResourceIdsWithPermission(authenticatedUser, ReferenceType.APPLICATION, Permission.APPLICATION, Acl.READ)
                                        .toList()
                                        .flatMap(ids -> listAgentsFilteredByPermittedIds(domain, ids, page, clampedSize, query))))
                .map(apps -> new AgentApplicationPage(
                        apps.getData().stream().map(AgentApplication::of).toList(),
                        apps.getCurrentPage(),
                        apps.getTotalCount()))
                .subscribe(response::resume, response::resume);
    }

    private Single<Page<Application>> listAgents(String domain, int page, int size, String query) {
        if (query != null) {
            return applicationService.searchAgents(domain, query, page, size);
        }
        return applicationService.findAgentsByDomain(domain, page, size);
    }

    private Single<Page<Application>> listAgentsFilteredByPermittedIds(String domain, List<String> permittedIds, int page, int size, String query) {
        return listAgents(domain, page, size, query).map(original -> filterToPermittedIds(original, permittedIds, page));
    }

    private static Page<Application> filterToPermittedIds(Page<Application> page, List<String> permittedIds, int currentPage) {
        if (permittedIds == null || permittedIds.isEmpty()) {
            return new Page<>(List.of(), currentPage, 0L);
        }
        final List<Application> filtered = page.getData().stream()
                .filter(app -> permittedIds.contains(app.getId()))
                .toList();
        return new Page<>(filtered, currentPage, filtered.size());
    }

    public static final class AgentApplicationPage extends Page<AgentApplication> {
        public AgentApplicationPage(Collection<AgentApplication> data, int currentPage, long totalCount) {
            super(data, currentPage, totalCount);
        }
    }
}
