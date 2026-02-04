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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

public class ProtectedResourceMemberResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private ProtectedResourceService service;

    @DELETE
    @Operation(
            operationId = "removeProtectedResourceMember",
            summary = "Remove a membership",
            description = "User must have PROTECTED_RESOURCE_MEMBER[DELETE] permission on the specified protected resource " +
                    "or PROTECTED_RESOURCE_MEMBER[DELETE] permission on the specified domain " +
                    "or PROTECTED_RESOURCE_MEMBER[DELETE] permission on the specified environment " +
                    "or PROTECTED_RESOURCE_MEMBER[DELETE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Membership successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void removeMember(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("protected-resource") String protectedResourceId,
            @PathParam("member") String membershipId,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, ReferenceType.PROTECTED_RESOURCE, protectedResourceId, Permission.PROTECTED_RESOURCE_MEMBER, Acl.DELETE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> service.findById(protectedResourceId))
                        .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(protectedResourceId)))
                        .flatMapCompletable(__ -> membershipService.delete(Reference.protectedResource(protectedResourceId), membershipId, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
