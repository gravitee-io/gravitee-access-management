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
package io.gravitee.am.gateway.handler.management.api.resources.platform.plugins;

import io.gravitee.am.gateway.service.ExtensionGrantPluginService;
import io.gravitee.am.gateway.service.model.plugin.ExtensionGrantPlugin;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

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
    public ExtensionGrantPlugin getTokenGranterPlugin(
            @PathParam("extensionGrant") String extensionGrantId) {
        return extensionGrantPluginService.findById(extensionGrantId);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an extension grant's schema")
    public String getTokenGranterSchema(@PathParam("extensionGrant") String extensionGrantId) {
        // Check that the extension grant exists
        extensionGrantPluginService.findById(extensionGrantId);

        return extensionGrantPluginService.getSchema(extensionGrantId);
    }
}
