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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AlertTriggerService;
import io.gravitee.am.service.model.PatchAlertTrigger;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "alerts")
public class AlertTriggerResource extends AbstractResource {

    @Inject
    private AlertTriggerService alertTriggerService;

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateAlertTrigger",
            summary = "Update an alert trigger",
            description = "Update an alert trigger" +
                    "User must have DOMAIN_ALERT[UPDATE] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alert trigger successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AlertTrigger.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "alertTrigger", required = true) @Valid @NotNull PatchAlertTrigger patchAlertTrigger,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = this.getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN_ALERT, Acl.UPDATE)
                .andThen(alertTriggerService.createOrUpdate(ReferenceType.DOMAIN, domainId, patchAlertTrigger, authenticatedUser))
                .subscribe(response::resume, response::resume);
    }
}
