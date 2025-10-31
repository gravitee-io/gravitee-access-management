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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;
import static io.gravitee.am.repository.utils.RepositoryConstants.DEFAULT_MAX_CONCURRENCY;
import static io.gravitee.am.repository.utils.RepositoryConstants.DELAY_ERRORS;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnvironmentsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private EnvironmentService environmentService;

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listEnvironments",
            summary = "List all the environments",
            description = "User must have the ENVIRONMENT[LIST] permission on the specified organization " +
                    "AND either ENVIRONMENT[READ] permission on each environment " +
                    "or ENVIRONMENT[READ] permission on the specified organization." +
                    "Each returned environment is filtered and contains only basic information such as id and name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List all the environments of the organization",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = Environment.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @Suspended final AsyncResponse response) {

        User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ENVIRONMENT, Acl.LIST)
                .andThen(environmentService.findAll(organizationId))
                .flatMapMaybe(environment -> hasPermission(authenticatedUser,
                        or(of(ReferenceType.ENVIRONMENT, environment.getId(), Permission.ENVIRONMENT, Acl.READ),
                                of(ReferenceType.ORGANIZATION, organizationId, Permission.ENVIRONMENT, Acl.READ)))
                        .filter(Boolean::booleanValue).map(permit -> environment), DELAY_ERRORS, DEFAULT_MAX_CONCURRENCY)
                .map(this::filterEnvironmentInfos)
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                .toList()
                .subscribe(response::resume, response::resume);
    }

    private Environment filterEnvironmentInfos(Environment environment) {

        Environment filteredEnvironment = new Environment();
        filteredEnvironment.setId(environment.getId());
        filteredEnvironment.setName(environment.getName());
        filteredEnvironment.setDescription(environment.getDescription());
        filteredEnvironment.setDomainRestrictions(environment.getDomainRestrictions());
        filteredEnvironment.setHrids(environment.getHrids());

        return filteredEnvironment;
    }

    @Path("/{environmentId}")
    public EnvironmentResource getEnvironmentResource() {
        return resourceContext.getResource(EnvironmentResource.class);
    }
}
