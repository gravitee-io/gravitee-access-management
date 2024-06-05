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
import io.gravitee.am.management.service.ExtensionGrantPluginService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ExtensionGrantService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewExtensionGrant;
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
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "extension grant")
public class ExtensionGrantsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ExtensionGrantService extensionGrantService;

    @Autowired
    private ExtensionGrantPluginService extensionGrantPluginService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List registered extension grants for a security domain",
            description = "User must have the DOMAIN_EXTENSION_GRANT[LIST] permission on the specified domain " +
                    "or DOMAIN_EXTENSION_GRANT[LIST] permission on the specified environment " +
                    "or DOMAIN_EXTENSION_GRANT[LIST] permission on the specified organization. " +
                    "Each returned extension grant is filtered and contains only basic information such as id, name and type.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List registered extension grants for a security domain",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ExtensionGrant.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_EXTENSION_GRANT, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> extensionGrantService.findByDomain(domain)
                                .map(this::filterExtensionGrantInfos)
                                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName())).toList())
                        .map(sortedExtensionGrants -> Response.ok(sortedExtensionGrants).build()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a extension grant",
            description = "User must have the DOMAIN_EXTENSION_GRANT[CREATE] permission on the specified domain " +
                    "or DOMAIN_EXTENSION_GRANT[CREATE] permission on the specified environment " +
                    "or DOMAIN_EXTENSION_GRANT[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Extension grant successfully created"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Parameter(name = "extension grant", required = true)
            @Valid @NotNull final NewExtensionGrant newExtensionGrant,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_EXTENSION_GRANT, Acl.CREATE)
                .andThen(extensionGrantPluginService.checkPluginDeployment(newExtensionGrant.getType()))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> extensionGrantService.create(domain, newExtensionGrant, authenticatedUser)
                                .map(extensionGrant -> Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/extensionGrants/" + extensionGrant.getId()))
                                        .entity(extensionGrant)
                                        .build())))
                .subscribe(response::resume, response::resume);
    }

    @Path("{extensionGrant}")
    public ExtensionGrantResource getTokenGranterResource() {
        return resourceContext.getResource(ExtensionGrantResource.class);
    }

    private ExtensionGrant filterExtensionGrantInfos(ExtensionGrant extensionGrant) {
        ExtensionGrant filteredExtensionGrant = new ExtensionGrant();
        filteredExtensionGrant.setId(extensionGrant.getId());
        filteredExtensionGrant.setName(extensionGrant.getName());
        filteredExtensionGrant.setType(extensionGrant.getType());
        filteredExtensionGrant.setGrantType(extensionGrant.getGrantType());

        return filteredExtensionGrant;
    }
}
