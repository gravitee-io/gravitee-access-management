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

import io.gravitee.am.management.handlers.management.api.model.AlertServiceStatusEntity;
import io.gravitee.am.management.service.AlertService;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.SpelService;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConfigurationResource {

    @Inject
    private FlowService flowService;

    @Inject
    private AlertService alertService;

    @Inject
    private SpelService spelService;

    @GET
    @Path("/flow/schema")
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the Policy Studio flow schema",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void list(@Suspended final AsyncResponse response) {
        flowService.getSchema()
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("alerts/status")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the alert service status",
            notes = "There is no particular permission needed. User must be authenticated.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Current alert service status", response = AlertServiceStatusEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void getAlertServiceStatus(@Suspended final AsyncResponse response) {

        alertService.isAlertingAvailable()
                .map(AlertServiceStatusEntity::new)
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("spel/grammar")
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the spel grammar",
        notes = "There is no particular permission needed. User must be authenticated.")
    public void getSpelGrammar(@Suspended final AsyncResponse response) {
        spelService.getGrammar()
            .subscribe(response::resume, response::resume);
    }

}
