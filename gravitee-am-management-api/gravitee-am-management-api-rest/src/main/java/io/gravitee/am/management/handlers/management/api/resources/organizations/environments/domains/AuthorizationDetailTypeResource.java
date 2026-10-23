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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AuthorizationDetailTypeService;
import io.gravitee.am.service.exception.AuthorizationDetailTypeNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * @author GraviteeSource Team
 */
@Tag(name = "authorization detail type")
public class AuthorizationDetailTypeResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private AuthorizationDetailTypeService authorizationDetailTypeService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getAuthorizationDetailType",
            summary = "Get an authorization detail type",
            description = "User must have the DOMAIN_AUTHORIZATION_DETAIL_TYPE[READ] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_DETAIL_TYPE[READ] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_DETAIL_TYPE[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization detail type",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationDetailType.class))),
            @ApiResponse(responseCode = "404", description = "Authorization detail type not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void read(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("authorizationDetailTypeId") String authorizationDetailTypeId,
            @Suspended final AsyncResponse response) {
        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_DETAIL_TYPE, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMap(domain -> authorizationDetailTypeService.findById(domainId, authorizationDetailTypeId))
                .switchIfEmpty(Maybe.error(new AuthorizationDetailTypeNotFoundException(authorizationDetailTypeId)))
                .subscribe(response::resume, response::resume);
    }
}
