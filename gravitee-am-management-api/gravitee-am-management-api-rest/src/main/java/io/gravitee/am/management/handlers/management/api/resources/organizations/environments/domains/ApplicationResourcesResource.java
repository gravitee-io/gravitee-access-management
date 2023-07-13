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
import io.gravitee.am.management.handlers.management.api.model.ResourceListItem;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ResourceService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import java.util.Collections;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationResourcesResource extends AbstractResource {

    private static final int MAX_RESOURCES_SIZE_PER_PAGE = 50;
    private static final String MAX_RESOURCES_SIZE_PER_PAGE_STRING = "50";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ResourceService resourceService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List resources for an application",
            notes = "User must have APPLICATION_RESOURCE[LIST] permission on the specified application " +
                    "or APPLICATION_RESOURCE[LIST] permission on the specified domain " +
                    "or APPLICATION_RESOURCE[LIST] permission on the specified environment " +
                    "or APPLICATION_RESOURCE[LIST] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List resources for an application", response = ResourceListItem.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_RESOURCES_SIZE_PER_PAGE_STRING) int size,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION_RESOURCE, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> applicationService.findById(application))
                        .switchIfEmpty(Single.error(new ApplicationNotFoundException(application)))
                        .flatMap(application1 -> resourceService.findByDomainAndClient(domain, application1.getId(), page, Integer.min(MAX_RESOURCES_SIZE_PER_PAGE, size)))
                        .flatMap(pagedResources -> Observable.fromIterable(pagedResources.getData())
                                .flatMapSingle(r -> resourceService.countAccessPolicyByResource(r.getId())
                                        .map(policies -> {
                                            ResourceEntity resourceEntity = new ResourceEntity(r);
                                            resourceEntity.setPolicies(policies);
                                            return resourceEntity;
                                        }))
                                .toList()
                                .zipWith(resourceService.getMetadata((List<Resource>) pagedResources.getData()), (v1, v2) ->
                                        new Page<>(Collections.singletonList(new ResourceListItem(v1, v2)), page, pagedResources.getTotalCount())))
                )
                .subscribe(response::resume, response::resume);
    }

    @Path("{resource}")
    public ApplicationResourceResource getResourceResource() {
        return resourceContext.getResource(ApplicationResourceResource.class);
    }
}
