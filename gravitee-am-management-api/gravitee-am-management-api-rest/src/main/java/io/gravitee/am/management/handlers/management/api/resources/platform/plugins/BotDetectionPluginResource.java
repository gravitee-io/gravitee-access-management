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

import io.gravitee.am.management.service.BotDetectionPluginService;
import io.gravitee.am.management.service.exception.BotDetectionPluginNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import jakarta.inject.Inject;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "Plugin"), @Tag(name= "Bot Detection")})
public class BotDetectionPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private BotDetectionPluginService pluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a Bot Detection plugin",
            description = "There is no particular permission needed. User must be authenticated.")
    public void get(@PathParam("botDetection") String botDetectionId,
                    @Suspended final AsyncResponse response) {

        pluginService.findById(botDetectionId)
                .switchIfEmpty(Maybe.error(new BotDetectionPluginNotFoundException(botDetectionId)))
                .map(policyPlugin -> Response.ok(policyPlugin).build())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a Bot Detection plugin's schema",
            description = "There is no particular permission needed. User must be authenticated.")
    public void getSchema(@PathParam("botDetection") String botDetection,
                          @Suspended final AsyncResponse response) {

        // Check that the authenticator exists
        pluginService.findById(botDetection)
                .flatMap(irrelevant -> pluginService.getSchema(botDetection))
                .map(policyPluginSchema -> Response.ok(policyPluginSchema).build())
                .switchIfEmpty(Maybe.just(Response.noContent().build()))
                .subscribe(response::resume, response::resume);
    }
}