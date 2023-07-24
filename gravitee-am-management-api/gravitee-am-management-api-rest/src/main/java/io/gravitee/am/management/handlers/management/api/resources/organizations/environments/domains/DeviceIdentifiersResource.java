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
import io.gravitee.am.management.service.DeviceIdentifierPluginService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.DeviceIdentifierService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewDeviceIdentifier;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"device identifiers"})
public class DeviceIdentifiersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DeviceIdentifierService deviceIdentifierService;

    @Autowired
    private DeviceIdentifierPluginService deviceIdentifierPluginService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered device identifiers for a security domain",
            notes = "User must have the DOMAIN_DEVICE_IDENTIFIERS[LIST] permission on the specified domain " +
                    "or DOMAIN_DEVICE_IDENTIFIERS[LIST] permission on the specified environment " +
                    "or DOMAIN_DEVICE_IDENTIFIERS[LIST] permission on the specified organization " +
                    "Each returned bot detections is filtered and contains only basic information such as id, name.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered device identifiers for a security domain", response = BotDetection.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DEVICE_IDENTIFIER, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(___ -> deviceIdentifierService.findByDomain(domain).map(this::filterDeviceIdentifierInfo).toList()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a device identifier",
            notes = "User must have the DOMAIN_DEVICE_IDENTIFIER[CREATE] permission on the specified domain " +
                    "or DOMAIN_DEVICE_IDENTIFIER[CREATE] permission on the specified environment " +
                    "or DOMAIN_DEVICE_IDENTIFIER[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Device identifiers successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @ApiParam(name = "deviceIdentifier", required = true) @Valid @NotNull final NewDeviceIdentifier newDeviceIdentifier,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DEVICE_IDENTIFIER, Acl.CREATE)
                .andThen(deviceIdentifierPluginService.checkPluginDeployment(newDeviceIdentifier.getType()))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> deviceIdentifierService.create(domain, newDeviceIdentifier, authenticatedUser))
                        .map(deviceIdentifier -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/device-identifiers/" + deviceIdentifier.getId()))
                                .entity(deviceIdentifier)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{deviceIdentifier}")
    public DeviceIdentifierResource getDeviceIdentifierResource() {
        return resourceContext.getResource(DeviceIdentifierResource.class);
    }

    private DeviceIdentifier filterDeviceIdentifierInfo(DeviceIdentifier deviceIdentifier) {
        var filteredDeviceIdentifier = new DeviceIdentifier();
        filteredDeviceIdentifier.setId(deviceIdentifier.getId());
        filteredDeviceIdentifier.setName(deviceIdentifier.getName());
        filteredDeviceIdentifier.setType(deviceIdentifier.getType());
        return filteredDeviceIdentifier;
    }
}
