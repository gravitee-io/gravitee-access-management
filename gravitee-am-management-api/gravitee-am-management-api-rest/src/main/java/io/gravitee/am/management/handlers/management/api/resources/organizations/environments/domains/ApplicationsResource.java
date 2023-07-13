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
import io.gravitee.am.model.Application;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewApplication;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
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

import static java.util.stream.Collectors.toList;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"application"})
public class ApplicationsResource extends AbstractResource {

    private static final String MAX_APPLICATIONS_SIZE_PER_PAGE_STRING = "50";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "listApplications",
            value = "List registered applications for a security domain",
            notes = "User must have the APPLICATION[LIST] permission on the specified domain, environment or organization " +
                    "AND either APPLICATION[READ] permission on each domain's application " +
                    "or APPLICATION[READ] permission on the specified domain " +
                    "or APPLICATION[READ] permission on the specified environment " +
                    "or APPLICATION[READ] permission on the specified organization. " +
                    "Each returned application is filtered and contains only basic information such as id, name, description and isEnabled.")
    @ApiResponses({
            @ApiResponse(code = 200,
                    message = "List registered applications for a security domain",
                    response = ApplicationPage.class
            ),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_APPLICATIONS_SIZE_PER_PAGE_STRING) int size,
            @QueryParam("q") String query,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Single.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> {
                            if (query != null) {
                                return applicationService.search(domain, query, 0, Integer.MAX_VALUE);
                            } else {
                                return applicationService.findByDomain(domain, 0, Integer.MAX_VALUE);
                            }
                        })
                        .flatMap(pagedApplications -> Maybe.concat(
                                pagedApplications.getData().stream()
                                        .map(application -> hasAnyPermission(authenticatedUser, organizationId, environmentId, domain, application.getId(), Permission.APPLICATION, Acl.READ)
                                                .filter(Boolean::booleanValue)
                                                .map(__ -> filterApplicationInfos(application)))
                                        .collect(toList()))
                                .sorted((a1, a2) -> a2.getUpdatedAt().compareTo(a1.getUpdatedAt()))
                                .toList()
                                .map(applications -> new ApplicationPage(
                                        applications.stream().skip(page * size).limit(size).collect(toList()),
                                        page,
                                        applications.size()))))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "createApplication",
            value = "Create an application",
            notes = "User must have APPLICATION[CREATE] permission on the specified domain " +
                    "or APPLICATION[CREATE] permission on the specified environment " +
                    "or APPLICATION[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Application successfully created", response = Application.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void createApplication(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @ApiParam(name = "application", required = true)
            @Valid @NotNull final NewApplication newApplication,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> applicationService.create(domain, newApplication, authenticatedUser)
                                .map(application -> Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/applications/" + application.getId()))
                                        .entity(application)
                                        .build())))
                .subscribe(response::resume, response::resume);
    }

    @Path("{application}")
    public ApplicationResource getApplicationResource() {
        return resourceContext.getResource(ApplicationResource.class);
    }

    private Application filterApplicationInfos(Application application) {
        Application filteredApplication = new Application();
        filteredApplication.setId(application.getId());
        filteredApplication.setName(application.getName());
        filteredApplication.setDescription(application.getDescription());
        filteredApplication.setType(application.getType());
        filteredApplication.setEnabled(application.isEnabled());
        filteredApplication.setTemplate(application.isTemplate());
        filteredApplication.setUpdatedAt(application.getUpdatedAt());

        return filteredApplication;
    }

    public static final class ApplicationPage extends Page<Application> {
        public ApplicationPage(Collection<Application> data, int currentPage, long totalCount) {
            super(data, currentPage, totalCount);
        }
    }
}
