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
import io.gravitee.am.management.service.dataplane.DeviceManagementService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private DeviceManagementService deviceService;

    @DELETE
    @Operation(
            operationId = "deleteUserDevice",
            summary = "Delete a device",
            description = "User must have the DOMAIN_USER_DEVICE[DELETE] permission on the specified domain " +
                    "or DOMAIN_USER_DEVICE[DELETE] permission on the specified environment " +
                    "or DOMAIN_USER_DEVICE[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @PathParam("device") String device,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER_DEVICE, Acl.DELETE)
                .andThen(domainService.findById(domainId).switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapCompletable(domain -> deviceService.delete(domain, UserId.internal(user), device, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

}
