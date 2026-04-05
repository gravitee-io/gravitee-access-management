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

import io.gravitee.am.management.handlers.automation.model.AutomationDomainDefinition;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.AbstractNotFoundException;
import io.gravitee.am.service.model.AutomationNewDomain;
import io.reactivex.rxjava3.core.Single;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Tag(name = "Domains")
public class DomainsResource extends AbstractAutomationResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationListDomains", summary = "List all domains",
            description = "Returns all security domains within the specified environment.")
    @ApiResponse(responseCode = "200", description = "List of domains",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = Domain.class))))
    public void list(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN, Acl.LIST)
                .andThen(domainService.findAllByEnvironment(organizationId, environmentId)
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationCreateOrUpdateDomain",
            summary = "Create or update a domain",
            description = "Idempotent create-or-update. Uses the hrid field in the body to identify the domain.")
    @ApiResponse(responseCode = "200", description = "The created or updated domain",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Domain.class)))
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @Valid @NotNull AutomationDomainDefinition definition,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final String hrid = definition.getHrid();

        Single.defer(() ->
                domainService.findByHrid(environmentId, hrid)
                    .flatMap(existingDomain ->
                        checkAnyPermission(principal, organizationId, environmentId, existingDomain.getId(), Permission.DOMAIN, Acl.UPDATE)
                            .andThen(Single.defer(() -> {
                                existingDomain.setName(definition.getName());
                                existingDomain.setDescription(definition.getDescription());
                                existingDomain.setEnabled(definition.isEnabled());
                                if (definition.getPath() != null) {
                                    existingDomain.setPath(definition.getPath());
                                }
                                if (definition.getTags() != null) {
                                    existingDomain.setTags(definition.getTags());
                                }
                                return domainService.update(existingDomain.getId(), existingDomain);
                            })))
                    .onErrorResumeNext(throwable -> {
                        if (!(throwable instanceof AbstractNotFoundException)) {
                            return Single.error(throwable);
                        }
                        return checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN, Acl.CREATE)
                            .andThen(Single.defer(() -> {
                                AutomationNewDomain newDomain = new AutomationNewDomain();
                                newDomain.setId(deterministicId(environmentId, hrid));
                                newDomain.setName(definition.getName());
                                newDomain.setHrid(hrid);
                                newDomain.setPath(definition.getPath());
                                newDomain.setDescription(definition.getDescription());
                                newDomain.setDataPlaneId(definition.getDataPlaneId());
                                return domainService.create(organizationId, environmentId, newDomain, principal);
                            }));
                    }))
                .subscribe(response::resume, response::resume);
    }

    @Path("/{domainHrid}")
    public DomainResource getDomainResource() {
        return resourceContext.getResource(DomainResource.class);
    }

    private static String deterministicId(String environmentId, String hrid) {
        return UUID.nameUUIDFromBytes((environmentId + "/" + hrid).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
