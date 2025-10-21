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

import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResourcePrimaryData;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.model.ProtectedResource.Type.fromString;

public class ProtectedResourceResource extends AbstractDomainResource {

    @Autowired
    private ProtectedResourceService service;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findProtectedResource",
            summary = "Get a Protected Resource",
            description = "User must have the PROTECTED_RESOURCE[READ] permission on the specified resource " +
                    "or PROTECTED_RESOURCE[READ] permission on the specified domain " +
                    "or PROTECTED_RESOURCE[READ] permission on the specified environment " +
                    "or PROTECTED_RESOURCE[READ] permission on the specified organization. ")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Protected Resource",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProtectedResourcePrimaryData.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("protected-resource") String protectedResourceId,
            @QueryParam("type") String type,
            @Suspended final AsyncResponse response) {
        ProtectedResource.Type resourceType = fromString(type);

        checkAnyPermission(organizationId, environmentId, domainId, protectedResourceId, Permission.PROTECTED_RESOURCE, Acl.READ)
                .andThen(service.findByDomainAndIdAndType(domainId, protectedResourceId, resourceType)
                        .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(protectedResourceId))))
                .subscribe(response::resume, response::resume);
    }

}
