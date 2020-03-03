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

import io.gravitee.am.management.handlers.management.api.resources.dashboard.DashboardResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.PlatformResource;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.*;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api
public class EnvironmentResource extends AbstractResource {
    
    @Context
    private ResourceContext resourceContext;

    @Inject
    private EnvironmentService environmentService;

    /**
     * Delete an existing Environment.
     * @param environmentId
     * @return
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Delete an Environment", tags = {"Environment"})
    @ApiResponses({
            @ApiResponse(code = 204, message = "Environment successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response deleteEnvironment(
            @ApiParam(name = "environmentId", required = true)
            @PathParam("envId") String environmentId) {
        environmentService.delete(environmentId);
        //TODO: should delete all items that refers to this environment
        return Response
                .status(Status.NO_CONTENT)
                .build();
    }

    @Path("/domains")
    public DomainsResource getDomainsResource() {
        return resourceContext.getResource(DomainsResource.class);
    }

    @Path("/platform")
    public PlatformResource getPlatformResource() {
        return resourceContext.getResource(PlatformResource.class);
    }

    @Path("/dashboard")
    public DashboardResource getDashboardResource() {
        return resourceContext.getResource(DashboardResource.class);
    }

    @Path("/user")
    public CurrentUserResource getCurrentUserResource() {
        return resourceContext.getResource(CurrentUserResource.class);
    }
}
