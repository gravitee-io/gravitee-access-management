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
import io.gravitee.am.management.service.exception.DeviceIdentifierNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Device Identifier"})
public class DeviceIdentifierPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private DeviceIdentifierPluginService deviceIdentifierPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a device identifier plugin",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void get(@PathParam("deviceIdentifier") String deviceIdentifierId, @Suspended final AsyncResponse response) {

        deviceIdentifierPluginService.findById(deviceIdentifierId)
                .switchIfEmpty(Maybe.error(new DeviceIdentifierNotFoundException(deviceIdentifierId)))
                .map(policyPlugin -> Response.ok(policyPlugin).build())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a device identifier plugin's schema",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void getSchema(@PathParam("deviceIdentifier") String deviceIdentifierId, @Suspended final AsyncResponse response) {
        deviceIdentifierPluginService.findById(deviceIdentifierId)
                .flatMap(__ -> deviceIdentifierPluginService.getSchema(deviceIdentifierId))
                .map(policyPluginSchema -> Response.ok(policyPluginSchema).build())
                .switchIfEmpty(Maybe.just(Response.noContent().build()))
                .subscribe(response::resume, response::resume);
    }
}