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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.model.NewOrganization;
import io.gravitee.common.http.MediaType;
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

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Path("/organizations")
public class OrganizationsResource extends AbstractResource {

    @Autowired
    private OrganizationService organizationService;

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create or update an organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Organization successfully created or updated"),
            @ApiResponse(code = 500, message = "Internal server error")})
    // FIXME: for now there is no permission. This will be implemented in another dedicated feature.
    @Path("/{organizationId}")
    public void create(
            @PathParam("organizationId") String organizationId,
            @ApiParam(name = "organization", required = true)
            @Valid @NotNull final NewOrganization newOrganization,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        organizationService.createOrUpdate(organizationId, newOrganization, authenticatedUser)
                .subscribe(response::resume, response::resume);
    }
}
