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

import io.gravitee.am.management.handlers.management.api.model.CrossAppAccessResourceServerEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServerView;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.TrustDomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

@Tag(name = "cross-app-access")
public class CrossAppAccessResourceServersResource extends AbstractResource {

    static final int MAX_RESOURCE_SERVERS = 100;

    @Autowired
    private DomainService domainService;

    @Autowired
    private TrustDomainService trustDomainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listCrossAppAccessResourceServers",
            summary = "List the resource servers an application of this security domain may be mapped to",
            description = "Flattens the resource servers of every trusted domain that has Cross App Access " +
                    "enabled, keeping those the search term matches, up to the requested limit. " +
                    "User must have the DOMAIN_SETTINGS[READ] permission on the specified domain " +
                    "or DOMAIN_SETTINGS[READ] permission on the specified environment " +
                    "or DOMAIN_SETTINGS[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of resource servers",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = CrossAppAccessResourceServerEntity.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(description = "Case-insensitive term matched against the resource server name, its resource "
                    + "identifier and the name of the trusted domain exposing it. Empty keeps every resource server.")
            @QueryParam("q") @DefaultValue("") String query,
            @Parameter(description = "Maximum number of resource servers to return.")
            @QueryParam("limit") @DefaultValue("10") @Min(1) @Max(MAX_RESOURCE_SERVERS) int limit,
            @Suspended final AsyncResponse response) {
        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapPublisher(domain -> trustDomainService.searchCrossAppAccessResourceServers(
                        Reference.domain(domainId), query, limit))
                .map(CrossAppAccessResourceServersResource::toEntity)
                .toList()
                .subscribe(response::resume, response::resume);
    }

    private static CrossAppAccessResourceServerEntity toEntity(CrossAppAccessResourceServerView resourceServer) {
        return new CrossAppAccessResourceServerEntity(
                resourceServer.trustedDomainId(),
                resourceServer.trustedDomainName(),
                resourceServer.id(),
                resourceServer.name(),
                resourceServer.resource());
    }
}
