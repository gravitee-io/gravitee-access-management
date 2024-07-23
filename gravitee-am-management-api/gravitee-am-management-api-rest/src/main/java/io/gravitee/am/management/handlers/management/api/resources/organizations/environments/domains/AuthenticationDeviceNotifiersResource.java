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
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AuthenticationDeviceNotifierService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewAuthenticationDeviceNotifier;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "Authentication Device Notifier")
public class AuthenticationDeviceNotifiersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private AuthenticationDeviceNotifierService authDeviceNotifierService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List registered Authentication Device Notifiers for a security domain",
            description = "User must have the DOMAIN_AUTHDEVICE_NOTIFIER[LIST] permission on the specified domain " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[LIST] permission on the specified environment " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[LIST] permission on the specified organization " +
                    "Each returned Authentication Device Notifier is filtered and contains only basic information such as id, name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List registered Authentication Device Notifiers for a security domain",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AuthenticationDeviceNotifier.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_AUTHDEVICE_NOTIFIER, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(___ -> authDeviceNotifierService.findByDomain(domain).map(this::filterBotDetectionInfos).toList()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create an Authentication Device Notifier",
            description = "User must have the DOMAIN_AUTHDEVICE_NOTIFIER[CREATE] permission on the specified domain " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[CREATE] permission on the specified environment " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Authentication Device Notifier successfully created"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Parameter(name = "notifier", required = true) @Valid @NotNull final NewAuthenticationDeviceNotifier newDeviceNotifier,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_AUTHDEVICE_NOTIFIER, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> authDeviceNotifierService.create(domain, newDeviceNotifier, authenticatedUser))
                        .map(deviceNotifier -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/auth-device-notifiers/" + deviceNotifier.getId()))
                                .entity(deviceNotifier)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{authDeviceNotifier}")
    public AuthenticationDeviceNotifierResource getAuthenticationDeviceNotifierResource() {
        return resourceContext.getResource(AuthenticationDeviceNotifierResource.class);
    }

    private AuthenticationDeviceNotifier filterBotDetectionInfos(AuthenticationDeviceNotifier deviceNotifier) {
        AuthenticationDeviceNotifier filteredDeviceNotifier = new AuthenticationDeviceNotifier();
        filteredDeviceNotifier.setId(deviceNotifier.getId());
        filteredDeviceNotifier.setName(deviceNotifier.getName());
        filteredDeviceNotifier.setType(deviceNotifier.getType());
        return filteredDeviceNotifier;
    }
}
