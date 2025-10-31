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
import io.gravitee.am.service.validators.email.UserEmail;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.core.env.Environment;

import java.util.Map;

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
    @Inject
    private Environment environment;

    @GET
    @Path("/flow/schema")
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getPolicyStudioFlowSchema",
            summary = "Get the Policy Studio flow schema",
            description = "There is no particular permission needed. User must be authenticated.")
    public void list(@Suspended final AsyncResponse response) {
        flowService.getSchema()
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("alerts/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getAlertServiceStatus",
            summary = "Get the alert service status",
            description = "There is no particular permission needed. User must be authenticated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current alert service status",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AlertServiceStatusEntity.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getAlertServiceStatus(@Suspended final AsyncResponse response) {

        alertService.isAlertingAvailable()
                .map(AlertServiceStatusEntity::new)
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("spel/grammar")
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getSpelGrammar",
            summary = "Get the spel grammar",
            description = "There is no particular permission needed. User must be authenticated.")
    public void getSpelGrammar(@Suspended final AsyncResponse response) {
        spelService.getGrammar()
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("users/email-required")
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    public void getUserEmailRequired(@Suspended final AsyncResponse response) {
        var emailRequired = environment.getProperty(UserEmail.PROPERTY_USER_EMAIL_REQUIRED, boolean.class, true);
        response.resume(Map.of("emailRequired", emailRequired));
    }

}
