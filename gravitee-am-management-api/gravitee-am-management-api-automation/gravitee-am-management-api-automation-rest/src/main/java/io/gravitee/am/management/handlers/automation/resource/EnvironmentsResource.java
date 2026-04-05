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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.management.handlers.automation.model.AutomationEnvironmentDefinition;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.model.NewEnvironment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Tag(name = "Environments")
public class EnvironmentsResource extends AbstractAutomationResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private EnvironmentService environmentService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "automationListEnvironments",
            summary = "List all environments",
            description = "Returns all environments within the specified organization.")
    @ApiResponse(responseCode = "200", description = "List of environments",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = Environment.class))))
    public void list(
            @PathParam("orgId") String organizationId,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkPermission(principal, ReferenceType.ORGANIZATION, organizationId, Permission.ENVIRONMENT, Acl.LIST)
                .andThen(environmentService.findAll(organizationId)
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationCreateOrUpdateEnvironment",
            summary = "Create or update an environment",
            description = "Idempotent create-or-update. Uses the hrid field in the body to identify the environment.")
    @ApiResponse(responseCode = "200", description = "The created or updated environment",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Environment.class)))
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @Valid @NotNull AutomationEnvironmentDefinition definition,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        String envId = definition.getHrid();
        NewEnvironment newEnvironment = toNewEnvironment(definition);

        checkPermission(principal, ReferenceType.ORGANIZATION, organizationId, Permission.ENVIRONMENT, Acl.CREATE)
                .andThen(environmentService.createOrUpdate(organizationId, envId, newEnvironment, principal))
                .subscribe(response::resume, response::resume);
    }

    @Path("/{envId}")
    public EnvironmentResource getEnvironmentResource() {
        return resourceContext.getResource(EnvironmentResource.class);
    }

    private NewEnvironment toNewEnvironment(AutomationEnvironmentDefinition definition) {
        NewEnvironment env = new NewEnvironment();
        env.setName(definition.getName());
        env.setDescription(definition.getDescription());
        if (definition.getHrid() != null) {
            List<String> hrids = definition.getHrids() != null ? new ArrayList<>(definition.getHrids()) : new ArrayList<>();
            if (!hrids.contains(definition.getHrid())) {
                hrids.addFirst(definition.getHrid());
            }
            env.setHrids(hrids);
        } else {
            env.setHrids(definition.getHrids());
        }
        env.setDomainRestrictions(definition.getDomainRestrictions());
        return env;
    }
}
