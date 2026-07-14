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
package io.gravitee.am.management.handlers.management.api.resources.organizations;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.ConsoleUserPreferences;
import io.gravitee.am.model.Organization;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author GraviteeSource Team
 */
@Tag(name = "user")
public class CurrentUserPreferencesResource extends AbstractResource {

    @Autowired
    private OrganizationUserService organizationUserService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getConsolePreferences",
            summary = "Get the console preferences of the current user")
    @ApiResponse(responseCode = "200", description = "Current user console preferences successfully fetched",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ConsoleUserPreferences.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void get(@Suspended final AsyncResponse response) {
        if (!isAuthenticated()) {
            response.resume(new ForbiddenException());
            return;
        }

        final User authenticatedUser = getAuthenticatedUser();
        organizationUserService.getConsolePreferences(organizationId(authenticatedUser), authenticatedUser.getId())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "updateConsolePreferences",
            summary = "Update the console preferences of the current user")
    @ApiResponse(responseCode = "200", description = "Current user console preferences successfully updated",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ConsoleUserPreferences.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public void update(@Suspended final AsyncResponse response, @Valid @NotNull final ConsoleUserPreferences preferences) {
        if (!isAuthenticated()) {
            response.resume(new ForbiddenException());
            return;
        }

        final User authenticatedUser = getAuthenticatedUser();
        organizationUserService.updateConsolePreferences(organizationId(authenticatedUser), authenticatedUser.getId(), preferences, authenticatedUser)
                .subscribe(response::resume, response::resume);
    }

    private static String organizationId(User authenticatedUser) {
        return (String) authenticatedUser.getAdditionalInformation().getOrDefault(Claims.ORGANIZATION, Organization.DEFAULT);
    }
}
