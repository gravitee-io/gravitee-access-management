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
import io.gravitee.am.management.service.AlertNotifierServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.model.PatchAlertNotifier;
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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "alerts")
public class AlertNotifierResource extends AbstractResource {

    @Inject
    private AlertNotifierServiceProxy alertNotifierService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getAlertNotifier",
            summary = "Get an alert notifier",
            description = "Get an alert notifier by its id. " +
                    "User must have DOMAIN_ALERT_NOTIFIER[LIST] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The alert notifier",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AlertNotifier.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void getAlertNotifier(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("notifierId") String notifierId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN_ALERT_NOTIFIER, Acl.LIST)
                .andThen(alertNotifierService.getById(ReferenceType.DOMAIN, domainId, notifierId))
                .subscribe(response::resume, response::resume);
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "patchAlertNotifier",
            summary = "Update an alert notifier",
            description = "Update an alert notifier" +
                    "User must have DOMAIN_ALERT_NOTIFIER[UPDATE] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alert notifier successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AlertNotifier.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void patchAlertNotifier(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("notifierId") String notifierId,
            @Parameter(name = "alertNotifier", required = true) @Valid @NotNull PatchAlertNotifier patchAlertNotifier,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = this.getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN_ALERT_NOTIFIER, Acl.UPDATE)
                .andThen(alertNotifierService.update(ReferenceType.DOMAIN, domainId, notifierId, patchAlertNotifier, authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "deleteAlertNotifier",
            summary = "Delete an alert notifier",
            description = "Delete an alert notifier by its id. " +
                    "User must have DOMAIN_ALERT_NOTIFIER[DELETE] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Alert notifier successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void deleteAlertNotifier(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("notifierId") String notifierId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = this.getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN_ALERT_NOTIFIER, Acl.LIST)
                .andThen(alertNotifierService.delete(ReferenceType.DOMAIN, domainId, notifierId, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
