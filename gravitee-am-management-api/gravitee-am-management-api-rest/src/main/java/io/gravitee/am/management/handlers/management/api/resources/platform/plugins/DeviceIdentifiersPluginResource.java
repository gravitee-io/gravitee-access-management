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

import io.gravitee.am.management.service.DeviceIdentifierPluginService;
import io.gravitee.am.service.model.plugin.DeviceIdentifierPlugin;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Device Identifier"})
public class DeviceIdentifiersPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private DeviceIdentifierPluginService deviceIdentifierPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List device identifier plugins",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void list(@Suspended final AsyncResponse response) {

        deviceIdentifierPluginService.findAll()
                .map(authenticatorPlugins -> authenticatorPlugins.stream()
                        .sorted(Comparator.comparing(DeviceIdentifierPlugin::getName))
                        .collect(Collectors.toList()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{deviceIdentifier}")
    public DeviceIdentifierPluginResource getFactorPluginResource() {
        return resourceContext.getResource(DeviceIdentifierPluginResource.class);
    }
}