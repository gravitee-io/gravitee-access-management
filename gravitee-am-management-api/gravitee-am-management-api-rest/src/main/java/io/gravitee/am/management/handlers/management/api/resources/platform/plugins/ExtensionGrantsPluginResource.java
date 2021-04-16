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
import io.gravitee.am.service.model.plugin.ExtensionGrantPlugin;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.Comparator;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = { "Plugin", "Extension Grant" })
public class ExtensionGrantsPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private ExtensionGrantPluginService extensionGrantPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List extension grant plugins", notes = "There is no particular permission needed. User must be authenticated.")
    public void list(@Suspended final AsyncResponse response) {
        extensionGrantPluginService
            .findAll()
            .map(
                extensionGrantPlugins ->
                    extensionGrantPlugins.stream().sorted(Comparator.comparing(ExtensionGrantPlugin::getName)).collect(Collectors.toList())
            )
            .subscribe(response::resume, response::resume);
    }

    @Path("{extensionGrant}")
    public ExtensionGrantPluginResource getTokenGranterPluginResource() {
        return resourceContext.getResource(ExtensionGrantPluginResource.class);
    }
}
