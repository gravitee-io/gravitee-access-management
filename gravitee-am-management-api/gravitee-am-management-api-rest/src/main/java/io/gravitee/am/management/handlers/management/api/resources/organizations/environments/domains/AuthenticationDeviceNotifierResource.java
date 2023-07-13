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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AuthenticationDeviceNotifierService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.AuthenticationDeviceNotifierNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.UpdateAuthenticationDeviceNotifier;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationDeviceNotifierResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private AuthenticationDeviceNotifierService authDeviceNotifierService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an Authentication Device Notifier",
            notes = "User must have the DOMAIN_AUTHDEVICE_NOTIFIER[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Authentication Device Notifier successfully fetched", response = AuthenticationDeviceNotifier.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("authDeviceNotifier") String deviceNotifierId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_AUTHDEVICE_NOTIFIER, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> authDeviceNotifierService.findById(deviceNotifierId))
                        .switchIfEmpty(Maybe.error(new AuthenticationDeviceNotifierNotFoundException(deviceNotifierId)))
                        .map(deviceNotifier -> {
                            if (!deviceNotifier.getReferenceId().equalsIgnoreCase(domain) && !deviceNotifier.getReferenceType().equals(ReferenceType.DOMAIN)) {
                                throw new BadRequestException("AuthenticationDeviceNotifier does not belong to domain");
                            }
                            return Response.ok(deviceNotifier).build();
                        }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update an Authentication Device Notifier",
            notes = "User must have the DOMAIN_AUTHDEVICE_NOTIFIER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Authentication Device Notifier successfully updated", response = AuthenticationDeviceNotifier.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("authDeviceNotifier") String deviceNotifierId,
            @ApiParam(name = "notifier", required = true) @Valid @NotNull UpdateAuthenticationDeviceNotifier updateDeviceNotifier,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_AUTHDEVICE_NOTIFIER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> authDeviceNotifierService.update(domain, deviceNotifierId, updateDeviceNotifier, authenticatedUser)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete an Authentication Device Notifier",
            notes = "User must have the DOMAIN_AUTHDEVICE_NOTIFIER[DELETE] permission on the specified domain " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[DELETE] permission on the specified environment " +
                    "or DOMAIN_AUTHDEVICE_NOTIFIER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Authentication Device Notifier successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("authDeviceNotifier") String deviceNotifierId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_AUTHDEVICE_NOTIFIER, Acl.DELETE)
                .andThen(authDeviceNotifierService.delete(domain, deviceNotifierId, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}