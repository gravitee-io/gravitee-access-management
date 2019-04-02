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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ExtensionGrantService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewExtensionGrant;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"extension grant"})
public class ExtensionGrantsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ExtensionGrantService extensionGrantService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered extension grants for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered extension grants for a security domain", response = ExtensionGrant.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@PathParam("domain") String domain,
                     @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> extensionGrantService.findByDomain(domain)
                            .map(extensionGrants -> {
                                List<ExtensionGrant> sortedExtensionGrants = extensionGrants.stream()
                                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                                        .collect(Collectors.toList());
                                return Response.ok(sortedExtensionGrants).build();
                            })
                )
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a extension grant")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Extension grant successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("domain") String domain,
            @ApiParam(name = "extension grant", required = true)
            @Valid @NotNull final NewExtensionGrant newExtensionGrant,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> extensionGrantService.create(domain, newExtensionGrant, authenticatedUser)
                            .map(extensionGrant -> Response
                                    .created(URI.create("/domains/" + domain + "/extensionGrants/" + extensionGrant.getId()))
                                    .entity(extensionGrant)
                                    .build())
                )
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{extensionGrant}")
    public ExtensionGrantResource getTokenGranterResource() {
        return resourceContext.getResource(ExtensionGrantResource.class);
    }
}
