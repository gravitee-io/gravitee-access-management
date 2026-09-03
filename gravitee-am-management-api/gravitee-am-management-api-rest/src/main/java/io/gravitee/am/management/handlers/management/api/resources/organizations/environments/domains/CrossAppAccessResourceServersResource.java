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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.CrossAppAccessSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.TrustDomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

@Tag(name = "cross-app-access")
public class CrossAppAccessResourceServersResource extends AbstractResource {

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
                    "enabled. User must have the DOMAIN_SETTINGS[READ] permission on the specified domain " +
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
            @Suspended final AsyncResponse response) {
        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapPublisher(domain -> trustDomainService.findByReference(ReferenceType.DOMAIN, domainId))
                .flatMap(trustDomain -> Flowable.fromIterable(resourceServersOf(trustDomain)))
                .toList()
                .subscribe(response::resume, response::resume);
    }

    private static List<CrossAppAccessResourceServerEntity> resourceServersOf(TrustDomain trustDomain) {
        return Optional.ofNullable(trustDomain.getCrossAppAccess())
                .filter(CrossAppAccessSettings::isEnabled)
                .map(CrossAppAccessSettings::getResourceServers)
                .orElse(List.of())
                .stream()
                .map(resourceServer -> new CrossAppAccessResourceServerEntity(
                        trustDomain.getId(),
                        trustDomain.getName(),
                        resourceServer.getId(),
                        resourceServer.getName(),
                        resourceServer.getResource()))
                .toList();
    }
}
