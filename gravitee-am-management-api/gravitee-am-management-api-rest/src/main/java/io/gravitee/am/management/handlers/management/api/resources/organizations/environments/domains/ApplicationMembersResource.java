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
import io.gravitee.am.management.handlers.management.api.model.MembershipListItem;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewMembership;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationMembersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private ApplicationService applicationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List members for an application",
            notes = "User must have APPLICATION_MEMBER[LIST] permission on the specified application " +
                    "or APPLICATION_MEMBER[LIST] permission on the specified domain " +
                    "or APPLICATION_MEMBER[LIST] permission on the specified environment " +
                    "or APPLICATION_MEMBER[LIST] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List members for an application", response = MembershipListItem.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void getMembers(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION_MEMBER, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> applicationService.findById(application))
                        .switchIfEmpty(Single.error(new ApplicationNotFoundException(application)))
                        .flatMap(application1 -> membershipService.findByReference(application1.getId(), ReferenceType.APPLICATION).toList())
                        .flatMap(memberships -> membershipService.getMetadata(memberships).map(metadata -> new MembershipListItem(memberships, metadata))))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Add or update an application member",
            notes = "User must have APPLICATION_MEMBER[CREATE] permission on the specified application " +
                    "or APPLICATION_MEMBER[CREATE] permission on the specified domain " +
                    "or APPLICATION_MEMBER[CREATE] permission on the specified environment " +
                    "or APPLICATION_MEMBER[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Member has been added or updated successfully"),
            @ApiResponse(code = 400, message = "Membership parameter is not valid"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void addOrUpdateMember(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Valid @NotNull NewMembership newMembership,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        final Membership membership = convert(newMembership);
        membership.setDomain(domain);
        membership.setReferenceId(application);
        membership.setReferenceType(ReferenceType.APPLICATION);

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION_MEMBER, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMap(__ -> applicationService.findById(application))
                        .switchIfEmpty(Single.error(new ApplicationNotFoundException(application)))
                        .flatMap(__ -> membershipService.addOrUpdate(organizationId, membership, authenticatedUser))
                        .flatMap(membership1 -> membershipService.addDomainUserRoleIfNecessary(organizationId, environmentId, domain, newMembership, authenticatedUser)
                                .andThen(Single.just(Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/applications/" + application + "/members/" + membership1.getId()))
                                        .entity(membership1)
                                        .build()))))
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("permissions")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List application member's permissions",
            notes = "User must have APPLICATION[READ] permission on the specified application " +
                    "or APPLICATION[READ] permission on the specified domain " +
                    "or APPLICATION[READ] permission on the specified environment " +
                    "or APPLICATION[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application member's permissions", response = List.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void permissions(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, application, Permission.APPLICATION, Acl.READ)
                .andThen(permissionService.findAllPermissions(authenticatedUser, ReferenceType.APPLICATION, application)
                        .map(Permission::flatten))
                .subscribe(response::resume, response::resume);
    }

    @Path("{member}")
    public ApplicationMemberResource getMemberResource() {
        return resourceContext.getResource(ApplicationMemberResource.class);
    }

    private Membership convert(NewMembership newMembership) {
        Membership membership = new Membership();
        membership.setMemberId(newMembership.getMemberId());
        membership.setMemberType(newMembership.getMemberType());
        membership.setRoleId(newMembership.getRole());

        return membership;
    }
}
