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
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.gravitee.am.service.model.NewAlertNotifier;
import io.gravitee.am.service.validators.notifier.NotifierValidator;
import io.gravitee.am.service.validators.notifier.NotifierValidator.NotifierHolder;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "alerts")
public class AlertNotifiersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private AlertNotifierServiceProxy alertNotifierService;

    @Autowired
    private NotifierValidator notifierValidator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listAlertNotifiers",
            summary = "List alert notifiers",
            description = "List all the alert notifiers of the domain. " +
                    "User must have DOMAIN_ALERT_NOTIFIER[LIST] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List alert notifiers for current user",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AlertNotifier.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void listAlertNotifiers(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN_ALERT_NOTIFIER, Acl.LIST)
                .andThen(alertNotifierService.findByDomainAndCriteria(domainId, new AlertNotifierCriteria()))
                .sorted(Comparator.comparing(AlertNotifier::getCreatedAt))
                .toList()
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createAlertNotifier",
            summary = "Create an alert notifier",
            description = "Create a new alert notifier" +
                    "User must have DOMAIN_ALERT_NOTIFIER[CREATE] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alert notifier successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AlertNotifier.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void createAlertNotifier(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "alertNotifier", required = true) @Valid @NotNull NewAlertNotifier newAlertNotifier,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = this.getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN_ALERT_NOTIFIER, Acl.CREATE)
                .andThen(notifierValidator.validate(new NotifierHolder(newAlertNotifier.getType(), newAlertNotifier.getConfiguration())))
                .andThen(alertNotifierService.create(ReferenceType.DOMAIN, domainId, newAlertNotifier, authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @Path("/{notifierId}")
    @Operation(
            operationId = "getAlertNotifier",
            summary = "Get an alert notifier")
    public AlertNotifierResource getApplicationResource() {
        return resourceContext.getResource(AlertNotifierResource.class);
    }

}
