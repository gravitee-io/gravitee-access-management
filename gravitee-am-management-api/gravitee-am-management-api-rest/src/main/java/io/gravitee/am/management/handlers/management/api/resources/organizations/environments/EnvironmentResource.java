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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains.DomainsResource;
import io.gravitee.am.management.service.permissions.Permissions;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.model.NewEnvironment;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnvironmentResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private EnvironmentService environmentService;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create or update an environment",
            notes = "User must have the ORGANIZATION_ENVIRONMENT[CREATE] permission on the platform.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Environment successfully created or updated"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void newEnvironment(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @Valid @NotNull final NewEnvironment newEnvironment,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkPermissions(of(ReferenceType.PLATFORM, Platform.DEFAULT, Permission.ENVIRONMENT, Acl.CREATE))
                .andThen(environmentService.createOrUpdate(organizationId, environmentId, newEnvironment, authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @Path("/domains")
    public DomainsResource getDomainsResource() {
        return resourceContext.getResource(DomainsResource.class);
    }
}