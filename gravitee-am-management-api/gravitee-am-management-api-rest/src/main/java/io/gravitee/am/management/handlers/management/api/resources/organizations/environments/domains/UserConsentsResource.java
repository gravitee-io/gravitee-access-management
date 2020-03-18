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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.ApplicationEntity;
import io.gravitee.am.management.handlers.management.api.model.ScopeApprovalEntity;
import io.gravitee.am.management.handlers.management.api.model.ScopeEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

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
public class UserConsentsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ScopeApprovalService scopeApprovalService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ScopeService scopeService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a user consents")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User consents successfully fetched", response = ScopeApprovalEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_USER, acls = RolePermissionAction.READ)
    })
    public void list(@PathParam("domain") String domain,
                     @PathParam("user") String user,
                     @QueryParam("clientId") String clientId,
                     @Suspended final AsyncResponse response) {

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(__ -> {
                    if (clientId == null || clientId.isEmpty()) {
                        return scopeApprovalService.findByDomainAndUser(domain, user);
                    }
                    return scopeApprovalService.findByDomainAndUserAndClient(domain, user, clientId);
                })
                .flatMapObservable(scopeApprovals -> Observable.fromIterable(scopeApprovals))
                .flatMapSingle(scopeApproval ->
                        getClient(scopeApproval.getDomain(), scopeApproval.getClientId())
                                .zipWith(getScope(scopeApproval.getDomain(), scopeApproval.getScope()), ((clientEntity, scopeEntity) -> {
                                    ScopeApprovalEntity scopeApprovalEntity = new ScopeApprovalEntity(scopeApproval);
                                    scopeApprovalEntity.setClientEntity(clientEntity);
                                    scopeApprovalEntity.setScopeEntity(scopeEntity);
                                    return scopeApprovalEntity;
                                })))
                .toList()
                .map(scopeApprovals ->  Response.ok(scopeApprovals).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Revoke user consents")
    @ApiResponses({
            @ApiResponse(code = 204, message = "User consents successfully revoked"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_USER, acls = RolePermissionAction.UPDATE)
    })
    public void delete(@PathParam("domain") String domain,
                       @PathParam("user") String user,
                       @QueryParam("clientId") String clientId,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapCompletable(__ -> {
                    if (clientId == null || clientId.isEmpty()) {
                        return scopeApprovalService.revokeByUser(domain, user, authenticatedUser);
                    }
                    return scopeApprovalService.revokeByUserAndClient(domain, user, clientId, authenticatedUser);
                })
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
    }

    @Path("{consent}")
    public UserConsentResource getUserConsentResource() {
        return resourceContext.getResource(UserConsentResource.class);
    }

    private Single<ApplicationEntity> getClient(String domain, String clientId) {
        return applicationService.findByDomainAndClientId(domain, clientId)
                .map(application -> new ApplicationEntity(application))
                .defaultIfEmpty(new ApplicationEntity("unknown-id", clientId, "unknown-client-name"))
                .toSingle()
                .cache();
    }

    private Single<ScopeEntity> getScope(String domain, String scopeKey) {
        return scopeService.findByDomainAndKey(domain, scopeKey)
                .map(scope -> new ScopeEntity(scope))
                .defaultIfEmpty(new ScopeEntity("unknown-id", scopeKey, "unknown-scope-name", "unknown-scope-description"))
                .toSingle()
                .cache();
    }

}
