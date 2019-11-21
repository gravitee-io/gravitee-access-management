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
package io.gravitee.am.management.handlers.management.api.resources.platform.idps;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.DomainMasterNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an identity provider")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Identity provider", response = IdentityProvider.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_IDENTITY_PROVIDER, acls = RolePermissionAction.READ)
    })
    public void get(@PathParam("identity") String identityProvider,
                    @Suspended final AsyncResponse response) {
        domainService.findMaster()
                .switchIfEmpty(Maybe.error(new DomainMasterNotFoundException()))
                .flatMap(masterDomain -> identityProviderService.findById(identityProvider)
                        .switchIfEmpty(Maybe.error(new IdentityProviderNotFoundException(identityProvider)))
                        .map(identityProvider1 -> {
                            if (!identityProvider1.getDomain().equalsIgnoreCase(masterDomain.getId())) {
                                throw new BadRequestException("Identity provider does not belong to domain");
                            }
                            return Response.ok(identityProvider1).build();
                        })
                )
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update an identity provider")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Identity provider successfully updated", response = IdentityProvider.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_IDENTITY_PROVIDER, acls = RolePermissionAction.UPDATE)
    })
    public void update(@PathParam("identity") String identity,
                       @ApiParam(name = "identity", required = true) @Valid @NotNull UpdateIdentityProvider updateIdentityProvider,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findMaster()
                .switchIfEmpty(Maybe.error(new DomainMasterNotFoundException()))
                .flatMapSingle(masterDomain -> identityProviderService.update(masterDomain.getId(), identity, updateIdentityProvider, authenticatedUser))
                .flatMap(identityProvider -> identityProviderManager.reloadUserProvider(identityProvider))
                .map(identityProvider -> Response.ok(identityProvider).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Delete an identity provider")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Identity provider successfully deleted"),
            @ApiResponse(code = 400, message = "Identity provider is bind to existing clients"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_IDENTITY_PROVIDER, acls = RolePermissionAction.DELETE)
    })
    public void delete(@PathParam("identity") String identity,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findMaster()
                .switchIfEmpty(Maybe.error(new DomainMasterNotFoundException()))
                .flatMapCompletable(masterDomain -> identityProviderService.delete(masterDomain.getId(), identity, authenticatedUser))
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }
}
