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
import io.gravitee.am.management.service.exception.AuthenticationDeviceNotifierPluginNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
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
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Authentication Device Notifier"})
public class AuthenticationDeviceNotifierPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private AuthenticationDeviceNotifierPluginService pluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a Authentication Device Notifier plugin",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void get(@PathParam("authDeviceNotifierId") String authDeviceNotifierId,
                    @Suspended final AsyncResponse response) {

        pluginService.findById(authDeviceNotifierId)
                .switchIfEmpty(Maybe.error(new AuthenticationDeviceNotifierPluginNotFoundException(authDeviceNotifierId)))
                .map(policyPlugin -> Response.ok(policyPlugin).build())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an Authentication Device Notifier plugin's schema",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void getSchema(@PathParam("authDeviceNotifierId") String authDeviceNotifierId,
                          @Suspended final AsyncResponse response) {

        // Check that the auth device notifier exists
        pluginService.findById(authDeviceNotifierId)
                .flatMap(irrelevant -> pluginService.getSchema(authDeviceNotifierId))
                .map(policyPluginSchema -> Response.ok(policyPluginSchema).build())
                .switchIfEmpty(Maybe.just(Response.noContent().build()))
                .subscribe(response::resume, response::resume);
    }
}