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

import io.gravitee.am.management.handlers.management.api.model.ResourceEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ResourceService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationResourceResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private UserService userService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a resource for an application",
            notes = "User must have APPLICATION_RESOURCE[READ] permission on the specified application " +
                    "or APPLICATION_RESOURCE[READ] permission on the specified domain " +
                    "or APPLICATION_RESOURCE[READ] permission on the specified environment " +
                    "or APPLICATION_RESOURCE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Get a resource for an application", response = Resource.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @PathParam("resource") String resource,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION_RESOURCE, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> applicationService.findById(application))
                        .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                        .flatMap(application1 -> resourceService.findByDomainAndClientResource(domain, application1.getId(), resource)
                                .flatMap(r -> userService.findById(r.getUserId())
                                        .map(Optional::ofNullable)
                                        .defaultIfEmpty(Optional.empty())
                                        .map(optUser -> {
                                            ResourceEntity resourceEntity = new ResourceEntity(r);
                                            resourceEntity.setUserDisplayName(optUser.isPresent() ? optUser.get().getDisplayName() : "Unknown user");
                                            return resourceEntity;
                                        }).toMaybe())))
                .subscribe(response::resume, response::resume);
    }

    @Path("policies")
    public ApplicationResourcePoliciesResource getPoliciesResource() {
        return resourceContext.getResource(ApplicationResourcePoliciesResource.class);
    }
}
