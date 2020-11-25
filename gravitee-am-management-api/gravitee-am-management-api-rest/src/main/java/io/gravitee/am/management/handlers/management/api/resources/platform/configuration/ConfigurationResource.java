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
package io.gravitee.am.management.handlers.management.api.resources.platform.configuration;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.service.FlowService;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConfigurationResource {

    @Inject
    private FlowService flowService;

    @GET
    @Path("/flow/schema")
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the Policy Studio flow schema",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void list(@Suspended final AsyncResponse response) {
        flowService.getSchema()
                .subscribe(response::resume, response::resume);
    }
}
