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
package io.gravitee.am.management.handlers.management.api.resources.platform.plugins;

import io.gravitee.am.management.service.ExtensionGrantPluginService;
import io.gravitee.am.management.service.exception.ExtensionGrantPluginNotFoundException;
import io.gravitee.am.management.service.exception.ExtensionGrantPluginSchemaNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Extension Grant"})
public class ExtensionGrantPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private ExtensionGrantPluginService extensionGrantPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an extension grant plugin")
    public void get(
            @PathParam("extensionGrant") String extensionGrantId,
            @Suspended final AsyncResponse response) {
        extensionGrantPluginService.findById(extensionGrantId)
                .switchIfEmpty(Maybe.error(new ExtensionGrantPluginNotFoundException(extensionGrantId)))
                .map(extensionGrantPlugin -> Response.ok(extensionGrantPlugin).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an extension grant plugin's schema")
    public void getSchema(@PathParam("extensionGrant") String extensionGrantId,
                            @Suspended final AsyncResponse response) {
        // Check that the extension grant exists
        extensionGrantPluginService.findById(extensionGrantId)
                .switchIfEmpty(Maybe.error(new ExtensionGrantPluginNotFoundException(extensionGrantId)))
                .flatMap(irrelevant -> extensionGrantPluginService.getSchema(extensionGrantId))
                .switchIfEmpty(Maybe.error(new ExtensionGrantPluginSchemaNotFoundException(extensionGrantId)))
                .map(extensionGrantPluginSchema -> Response.ok(extensionGrantPluginSchema).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error)
                );
    }
}
