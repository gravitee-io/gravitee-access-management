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

import io.gravitee.am.management.service.AuthenticationDeviceNotifierPluginService;
import io.gravitee.am.service.model.plugin.AuthenticationDeviceNotifierPlugin;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Authentication Device Notifier"})
public class AuthenticationDeviceNotifiersPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private AuthenticationDeviceNotifierPluginService pluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List authentication device notifier plugins",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void list(@QueryParam("expand") List<String> expand, @Suspended final AsyncResponse response) {

        pluginService.findAll(expand)
                .map(plugins -> plugins.stream()
                        .sorted(Comparator.comparing(AuthenticationDeviceNotifierPlugin::getName))
                        .collect(Collectors.toList()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{authDeviceNotifierId}")
    public AuthenticationDeviceNotifierPluginResource getAuthenticationDeviceNotifierPluginResource() {
        return resourceContext.getResource(AuthenticationDeviceNotifierPluginResource.class);
    }
}