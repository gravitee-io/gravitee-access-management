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
import io.gravitee.am.management.handlers.management.api.resources.model.ApplicationExpand;
import io.gravitee.am.management.handlers.management.api.resources.model.FilteredApplication;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.model.ApplicationFilter;
import io.gravitee.am.service.model.NewApplication;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "application")
public class ApplicationsResource extends AbstractDomainResource {

    private static final String MAX_APPLICATIONS_SIZE_PER_PAGE_STRING = "50";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ApplicationService applicationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listApplications",
            summary = "List registered applications for a security domain",
            description = "User must have the APPLICATION[LIST] permission on the specified domain, environment or organization " +
                    "AND either APPLICATION[READ] permission on each domain's application " +
                    "or APPLICATION[READ] permission on the specified domain " +
                    "or APPLICATION[READ] permission on the specified environment " +
                    "or APPLICATION[READ] permission on the specified organization. " +
                    "Each returned application is filtered and contains only basic information such as id, name, description and isEnabled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "List registered applications for a security domain",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApplicationPage.class))
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_APPLICATIONS_SIZE_PER_PAGE_STRING) int size,
            @QueryParam("q") String query,
            @QueryParam("expand") List<String> expandsParam,
            @QueryParam("status") String status,
            @QueryParam("owner.email") String ownerEmail,
            @Suspended final AsyncResponse response) {
        User authenticatedUser = getAuthenticatedUser();
        ApplicationFilter filter = new ApplicationFilter(status, ownerEmail);

        // owner.email filter requires ORGANIZATION_USER[READ] — checked here, resolved in service
        io.reactivex.rxjava3.core.Completable ownerPermissionCheck = filter.hasOwnerEmailFilter()
                ? checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_USER, Acl.READ)
                : io.reactivex.rxjava3.core.Completable.complete();

        final Set<ApplicationExpand> expands = convertToApplicationExpands(expandsParam);
        ownerPermissionCheck
                .andThen(checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION, Acl.LIST))
                .andThen(checkDomainExists(domain).ignoreElement())
                .andThen(hasAnyPermission(authenticatedUser, organizationId, environmentId, domain, Permission.APPLICATION, Acl.READ)
                        .filter(hasPermission -> hasPermission)
                        .flatMapSingle(__ -> listApplications(domain, organizationId, filter, page, size, query))
                        .switchIfEmpty(
                                getResourceIdsWithPermission(authenticatedUser, ReferenceType.APPLICATION, Permission.APPLICATION, Acl.READ)
                                        .toList()
                                        .flatMap(ids -> listApplicationsByIds(domain, organizationId, ids, filter, page, size, query))))
                .map(apps ->
                        new ApplicationPage(
                                apps.getData().stream().map(app -> FilteredApplication.of(app, expands)).toList(),
                                apps.getCurrentPage(),
                                apps.getTotalCount())
                )
                .subscribe(response::resume, response::resume);
    }

    private Set<ApplicationExpand> convertToApplicationExpands(List<String> expandsParam) {
        return expandsParam == null ? Set.of() :
                expandsParam.stream()
                        .map(ApplicationExpand::fromString)
                        .filter(e -> e != null)
                        .collect(Collectors.toSet());
    }

    private Single<Page<Application>> listApplications(String domain, String organizationId, ApplicationFilter filter, int page, int size, String query) {
        if (filter.hasStatusFilter() || filter.hasOwnerEmailFilter()) {
            return query != null
                    ? applicationService.search(domain, organizationId, filter, query, page, size)
                    : applicationService.findByDomain(domain, organizationId, filter, page, size);
        }
        if (query != null) {
            return applicationService.search(domain, query, page, size);
        }
        return applicationService.findByDomain(domain, page, size);
    }

    private Single<Page<Application>> listApplicationsByIds(String domain, String organizationId, List<String> applicationIds, ApplicationFilter filter, int page, int size, String query) {
        if (filter.hasStatusFilter() || filter.hasOwnerEmailFilter()) {
            // Intersect permission-scoped IDs with any owner/status filter resolution in the service layer
            ApplicationFilter filterWithScope = new ApplicationFilter(filter.status(), filter.ownerEmail(), applicationIds);
            return query != null
                    ? applicationService.search(domain, organizationId, filterWithScope, query, page, size)
                    : applicationService.findByDomain(domain, organizationId, filterWithScope, page, size);
        }
        if (query != null) {
            return applicationService.search(domain, applicationIds, query, page, size);
        }
        return applicationService.findByDomain(domain, applicationIds, page, size);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createApplication",
            summary = "Create an application",
            description = "User must have APPLICATION[CREATE] permission on the specified domain " +
                    "or APPLICATION[CREATE] permission on the specified environment " +
                    "or APPLICATION[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Application.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void createApplication(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Parameter(name = "application", required = true)
            @Valid @NotNull final NewApplication newApplication,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION, Acl.CREATE)
                .andThen(checkDomainExists(domain)
                        .flatMap(existingDomain -> applicationService.create(existingDomain, newApplication, authenticatedUser)
                                .map(application -> Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/applications/" + application.getId()))
                                        .entity(application)
                                        .build())))
                .subscribe(response::resume, response::resume);
    }

    @Path("agents")
    public AgentApplicationsResource getAgentApplicationsResource() {
        return resourceContext.getResource(AgentApplicationsResource.class);
    }

    @Path("{application}")
    public ApplicationResource getApplicationResource() {
        return resourceContext.getResource(ApplicationResource.class);
    }

    public static final class ApplicationPage extends Page<FilteredApplication> {
        public ApplicationPage(Collection<FilteredApplication> data, int currentPage, long totalCount) {
            super(data, currentPage, totalCount);
        }
    }
}
