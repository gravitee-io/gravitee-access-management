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
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.model.NewEnvironment;
import io.gravitee.common.http.MediaType;
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
@Path("/organizations/{organizationId}/environments")
public class EnvironmentsResource extends AbstractResource {

    public static final String ENVIRONMENTS_TAG = "Environments";

    @Autowired
    private EnvironmentService environmentService;

    @PUT
    @Path("/{environmentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void newEnvironment(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @Valid @NotNull final NewEnvironment newEnvironment,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        environmentService.createOrUpdate(organizationId, environmentId, newEnvironment, authenticatedUser)
                .subscribe(response::resume, response::resume);
    }
}