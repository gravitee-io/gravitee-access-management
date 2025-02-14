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
import io.gravitee.am.management.service.ResourcePluginService;
import io.gravitee.am.management.service.ServiceResourceServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewServiceResource;
import io.gravitee.am.service.validators.resource.ResourceValidator;
import io.gravitee.am.service.validators.resource.ResourceValidator.ResourceHolder;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "resource")
public class ServiceResourcesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ServiceResourceServiceProxy resourceService;

    @Autowired
    private ResourcePluginService resourcePluginService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ResourceValidator resourceValidator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List registered resources for a security domain",
            operationId = "listResources",
            description = "User must have the DOMAIN_RESOURCE[LIST] permission on the specified domain " +
                    "or DOMAIN_RESOURCE[LIST] permission on the specified environment " +
                    "or DOMAIN_RESOURCE[LIST] permission on the specified organization " +
                    "Each returned resource is filtered and contains only basic information such as id, name and resource type.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List registered resources for a security domain",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ServiceResource.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_FACTOR, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapPublisher(___ -> resourceService.findByDomain(domain))
                        .map(this::filterFactorInfos)
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a resource",
            operationId = "createResource",
            description = "User must have the DOMAIN_RESOURCE[CREATE] permission on the specified domain " +
                    "or DOMAIN_RESOURCE[CREATE] permission on the specified environment " +
                    "or DOMAIN_RESOURCE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Resource successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ServiceResource.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Parameter(name = "resource", required = true) @Valid @NotNull final NewServiceResource newResource,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_RESOURCE, Acl.CREATE)
                .andThen(resourcePluginService.checkPluginDeployment(newResource.getType()))
                .andThen(resourceValidator.validate(new ResourceHolder(newResource.getType(), newResource.getConfiguration())))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(existingDomain -> resourceService.create(existingDomain, newResource, authenticatedUser))
                        .map(resource -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/resources/" + resource.getId()))
                                .entity(resource)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{resource}")
    public ServiceResourceResource getServiceResourceResource() {
        return resourceContext.getResource(ServiceResourceResource.class);
    }

    private ServiceResource filterFactorInfos(ServiceResource resource) {
        ServiceResource filteredResource = new ServiceResource();
        filteredResource.setId(resource.getId());
        filteredResource.setName(resource.getName());
        filteredResource.setType(resource.getType());
        return filteredResource;
    }
}
