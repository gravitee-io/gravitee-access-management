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
import io.gravitee.am.management.service.exception.DeviceIdentifierNotFoundException;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.DeviceIdentifierService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateDeviceIdentifier;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierResource extends AbstractResource {

    @Autowired
    private DeviceIdentifierService deviceIdentifierService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a Device identifier",
            notes = "User must have the DOMAIN_DEVICE_IDENTIFIER[READ] permission on the specified domain " +
                    "or DOMAIN_DEVICE_IDENTIFIER[READ] permission on the specified environment " +
                    "or DOMAIN_DEVICE_IDENTIFIER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Device identifier successfully fetched", response = DeviceIdentifierResource.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("deviceIdentifier") String deviceIdentifierId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DEVICE_IDENTIFIER, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> deviceIdentifierService.findById(deviceIdentifierId))
                        .switchIfEmpty(Maybe.error(new DeviceIdentifierNotFoundException(deviceIdentifierId)))
                        .map(deviceIdentifier -> {
                            if (!(deviceIdentifier.getReferenceId().equalsIgnoreCase(domain) && deviceIdentifier.getReferenceType().equals(ReferenceType.DOMAIN))) {
                                throw new BadRequestException("DeviceIdentifier does not belong to domain");
                            }
                            return Response.ok(deviceIdentifier).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a Device identifier",
            notes = "User must have the DOMAIN_DEVICE_IDENTIFIER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_DEVICE_IDENTIFIER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_DEVICE_IDENTIFIER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Device identifier successfully updated", response = BotDetection.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("deviceIdentifier") String deviceIdentifierId,
            @ApiParam(name = "deviceIdentifier", required = true) @Valid @NotNull UpdateDeviceIdentifier updateBotDetection,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DEVICE_IDENTIFIER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> deviceIdentifierService.update(domain, deviceIdentifierId, updateBotDetection, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete a Device identifier",
            notes = "User must have the DOMAIN_DEVICE_IDENTIFIER[DELETE] permission on the specified domain " +
                    "or DOMAIN_DEVICE_IDENTIFIER[DELETE] permission on the specified environment " +
                    "or DOMAIN_DEVICE_IDENTIFIER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Device identifier successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("deviceIdentifier") String deviceIdentifierId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DEVICE_IDENTIFIER, Acl.DELETE)
                .andThen(deviceIdentifierService.delete(domain, deviceIdentifierId, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}