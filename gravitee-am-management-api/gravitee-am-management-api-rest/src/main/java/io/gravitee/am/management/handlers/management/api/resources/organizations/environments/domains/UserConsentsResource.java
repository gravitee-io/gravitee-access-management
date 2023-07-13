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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

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
    @ApiOperation(value = "Get a user consents",
            notes = "User must have the DOMAIN_USER[READ] permission on the specified domain " +
                    "or DOMAIN_USER[READ] permission on the specified environment " +
                    "or DOMAIN_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User consents successfully fetched", response = ScopeApprovalEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @QueryParam("clientId") String clientId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.READ)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapPublisher(__ -> {
                            if (clientId == null || clientId.isEmpty()) {
                                return scopeApprovalService.findByDomainAndUser(domain, user);
                            }
                            return scopeApprovalService.findByDomainAndUserAndClient(domain, user, clientId);
                        })
                        .flatMapSingle(scopeApproval ->
                                getClient(scopeApproval.getDomain(), scopeApproval.getClientId())
                                        .zipWith(getScope(scopeApproval.getDomain(), scopeApproval.getScope()), ((clientEntity, scopeEntity) -> {
                                            ScopeApprovalEntity scopeApprovalEntity = new ScopeApprovalEntity(scopeApproval);
                                            scopeApprovalEntity.setClientEntity(clientEntity);
                                            scopeApprovalEntity.setScopeEntity(scopeEntity);
                                            return scopeApprovalEntity;
                                        })))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(value = "Revoke user consents",
            notes = "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 204, message = "User consents successfully revoked"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @QueryParam("clientId") String clientId,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapCompletable(__ -> {
                            if (clientId == null || clientId.isEmpty()) {
                                return scopeApprovalService.revokeByUser(domain, user, authenticatedUser);
                            }
                            return scopeApprovalService.revokeByUserAndClient(domain, user, clientId, authenticatedUser);
                        }))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @Path("{consent}")
    public UserConsentResource getUserConsentResource() {
        return resourceContext.getResource(UserConsentResource.class);
    }

    private Single<ApplicationEntity> getClient(String domain, String clientId) {
        return applicationService.findByDomainAndClientId(domain, clientId)
                .map(ApplicationEntity::new)
                .defaultIfEmpty(new ApplicationEntity("unknown-id", clientId, "unknown-client-name"))
                .cache();
    }

    private Single<ScopeEntity> getScope(String domain, String scopeKey) {
        return scopeService.findByDomainAndKey(domain, scopeKey)
                .switchIfEmpty(scopeService.findByDomainAndKey(domain, getScopeBase(scopeKey)).map(entity -> {
                    // set the right scopeKey since the one returned by the service contains the scope definition without parameter
                    entity.setId("unknown-id");
                    entity.setKey(scopeKey);
                    return entity;
                }))
                .map(ScopeEntity::new)
                .defaultIfEmpty(new ScopeEntity("unknown-id", scopeKey, "unknown-scope-name", "unknown-scope-description"))
                .cache();
    }

    private String getScopeBase(String scope) {
        return scope.indexOf(':') > 0 ? scope.substring(0, scope.indexOf(':')) : scope;
    }
}
