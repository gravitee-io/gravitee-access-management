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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.BlueprintAgentService;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Manages the JWKS (public keys) on a Blueprint agent application.
 */
public class ApplicationAgentKeysResource extends AbstractResource {

    @Autowired
    private BlueprintAgentService blueprintAgentService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listAgentKeys",
            summary = "List public keys of a blueprint agent application",
            description = "User must have APPLICATION_OPENID[READ] permission on the specified application")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List agent keys",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = JWK.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void listKeys(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, ReferenceType.APPLICATION, application, Permission.APPLICATION_OPENID, Acl.READ)
                .andThen(blueprintAgentService.listAgentKeys(application))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "addAgentKey",
            summary = "Add a public key to a blueprint agent application",
            description = "User must have APPLICATION_OPENID[UPDATE] permission on the specified application")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key added successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = JWK.class))),
            @ApiResponse(responseCode = "400", description = "Invalid key or max keys reached"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void addKey(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            JWK key,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, ReferenceType.APPLICATION, application, Permission.APPLICATION_OPENID, Acl.UPDATE)
                .andThen(blueprintAgentService.addAgentKey(application, key))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Path("{kid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "removeAgentKey",
            summary = "Remove a public key from a blueprint agent application by kid",
            description = "User must have APPLICATION_OPENID[UPDATE] permission on the specified application")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key removed successfully"),
            @ApiResponse(responseCode = "400", description = "Key not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void removeKey(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @PathParam("kid") String kid,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, ReferenceType.APPLICATION, application, Permission.APPLICATION_OPENID, Acl.UPDATE)
                .andThen(blueprintAgentService.removeAgentKey(application, kid))
                .subscribe(response::resume, response::resume);
    }
}
