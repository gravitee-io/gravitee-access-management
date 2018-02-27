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

import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ExtensionGrantService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateExtensionGrant;
import io.gravitee.common.http.MediaType;
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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain", "oauth2"})
public class ExtensionGrantResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ExtensionGrantService extensionGrantService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a extension grant")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Extension grant successfully fetched", response = ExtensionGrant.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("domain") String domain,
            @PathParam("extensionGrant") String extensionGrant,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .isEmpty()
                .flatMapMaybe(isEmpty -> {
                    if (isEmpty) {
                        throw new DomainNotFoundException(domain);
                    } else {
                        return extensionGrantService.findById(extensionGrant)
                                .map(extensionGrant1 -> {
                                    if (!extensionGrant1.getDomain().equalsIgnoreCase(domain)) {
                                        return Response
                                                .status(Response.Status.BAD_REQUEST)
                                                .type(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE)
                                                .entity(new ErrorEntity("Extension grant does not belong to domain", Response.Status.BAD_REQUEST.getStatusCode()))
                                                .build();
                                    }
                                    return Response.ok(extensionGrant1).build();
                                })
                                .defaultIfEmpty(Response.status(Response.Status.NOT_FOUND)
                                        .type(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE)
                                        .entity(new ErrorEntity("Extension grant [" + extensionGrant + "] can not be found.", Response.Status.NOT_FOUND.getStatusCode()))
                                        .build());
                    }
                })
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a extension grant")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Extension grant successfully updated", response = ExtensionGrant.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("domain") String domain,
            @PathParam("extensionGrant") String extensionGrant,
            @ApiParam(name = "tokenGranter", required = true) @Valid @NotNull UpdateExtensionGrant updateExtensionGrant,
            @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (isEmpty) {
                        throw new DomainNotFoundException(domain);
                    } else {
                        return extensionGrantService.update(domain, extensionGrant, updateExtensionGrant);
                    }
                })
                .map(extensionGrant1 -> Response.ok(extensionGrant1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Delete a extension grant")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Extension grant successfully deleted"),
            @ApiResponse(code = 400, message = "Extension grant is bind to existing clients"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(@PathParam("domain") String domain,
                       @PathParam("extensionGrant") String extensionGrant,
                       @Suspended final AsyncResponse response) {
        extensionGrantService.delete(domain, extensionGrant)
                .map(irrelevant -> Response.noContent().build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }
}
