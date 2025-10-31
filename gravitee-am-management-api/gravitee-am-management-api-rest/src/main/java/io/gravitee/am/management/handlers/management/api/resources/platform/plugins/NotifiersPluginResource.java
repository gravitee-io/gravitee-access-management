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

import io.gravitee.am.management.service.impl.plugins.NotifierPluginService;
import io.gravitee.am.service.model.plugin.AbstractPlugin;
import io.gravitee.am.service.model.plugin.NotifierPlugin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.inject.Inject;
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

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "Plugin"), @Tag(name= "Notifier")})
public class NotifiersPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private NotifierPluginService notifierPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listNotifiers",
            summary = "List all available notifier plugins",
            description = "There is no particular permission needed. User must be authenticated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifier plugin list",
                    content = @Content(mediaType =  "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = NotifierPlugin.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(@QueryParam("expand") List<String> expand, @Suspended final AsyncResponse response) {

        notifierPluginService.findAll(expand.toArray(new String[0]))
                .sorted(Comparator.comparing(AbstractPlugin::getName))
                .toList()
                .subscribe(response::resume, response::resume);
    }

    @Path("{notifierId}")
    public NotifierPluginResource getNotifierPluginResource() {
        return resourceContext.getResource(NotifierPluginResource.class);
    }
}
