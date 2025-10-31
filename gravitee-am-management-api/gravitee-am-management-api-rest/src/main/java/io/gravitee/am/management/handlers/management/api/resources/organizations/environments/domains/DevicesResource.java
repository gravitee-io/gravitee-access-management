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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.ManagementUserService;
import io.gravitee.am.management.service.dataplane.DeviceManagementService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Rémi SULTAN (rémi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "devices")
public class DevicesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private DeviceManagementService deviceService;

    @Autowired
    private ManagementUserService userService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listUserDevices",
            summary = "List users for a security domain",
            description = "User must have the DOMAIN_USER_DEVICES[LIST] permission on the specified domain " +
                    "or DOMAIN_USER_DEVICES[LIST] permission on the specified environment " +
                    "or DOMAIN_USER_DEVICES[LIST] permission on the specified organization. ")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List users for a security domain",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = User.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String userId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER_DEVICE, Acl.LIST)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(domain ->
                                this.userService.findById(domain, userId)
                                        .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                                        .flatMapSingle(user -> this.deviceService.findByDomainAndUser(domain, user.getFullId()).toList())))
                .subscribe(response::resume, response::resume);
    }

    @Path("{device}")
    public DeviceResource getUserDevicesResource() {
        return resourceContext.getResource(DeviceResource.class);
    }
}
