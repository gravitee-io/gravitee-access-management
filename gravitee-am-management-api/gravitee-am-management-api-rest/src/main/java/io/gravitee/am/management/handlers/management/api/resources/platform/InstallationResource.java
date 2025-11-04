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
package io.gravitee.am.management.handlers.management.api.resources.platform;

import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import io.gravitee.am.management.handlers.management.api.model.InstallationEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Installation;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.InstallationService;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InstallationResource extends AbstractResource {

    protected static final String DEFAULT_COCKPIT_URL = "https://cockpit.gravitee.io";

    @Autowired
    private Environment environment;

    @Autowired
    private InstallationService installationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getInstallation",
            summary = "Get installation information",
            description = "User must have the INSTALLATION[READ] permission on the platform")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Installation successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InstallationEntity.class))),
            @ApiResponse(responseCode = "404", description = "No installation has been found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation =  ErrorEntity.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.PLATFORM, Platform.DEFAULT, Permission.INSTALLATION, Acl.READ)
                .andThen(installationService.get()
                        .map(InstallationEntity::new))
                .doOnSuccess(installationEntity -> installationEntity.getAdditionalInformation()
                        .put(Installation.COCKPIT_URL, getProperty("cockpit.url", "cloud.url", DEFAULT_COCKPIT_URL)))
                .subscribe(response::resume, response::resume);
    }

    private String getProperty(final String property, final String fallback, final String defaultValue) {
        String value = environment.getProperty(property, String.class);
        if (value == null) {
            value = environment.getProperty(fallback, String.class);
        }
        return value != null ? value : defaultValue;
    }

}
