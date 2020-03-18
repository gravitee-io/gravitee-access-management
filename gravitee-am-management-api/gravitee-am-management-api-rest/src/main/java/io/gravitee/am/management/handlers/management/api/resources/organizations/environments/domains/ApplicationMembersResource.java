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
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewMembership;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.swagger.annotations.ApiOperation;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupService groupService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List members for an application")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List members for an application", response = MembershipListItem.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.APPLICATION_MEMBER, acls = RolePermissionAction.READ)
    })
    public void list(@PathParam("domain") String domain,
                     @PathParam("application") String application,
                     @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(__ -> applicationService.findById(application))
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                .flatMapSingle(application1 -> membershipService.findByReference(application1.getId(), ReferenceType.APPLICATION))
                // fetch metadata
                .flatMap(memberships -> membershipService.getMetadata(memberships).map(metadata -> new MembershipListItem(memberships, metadata)))
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @POST
    @ApiOperation(value = "Add or update an application member")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Member has been added or updated successfully"),
            @ApiResponse(code = 400, message = "Membership parameter is not valid"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.APPLICATION_MEMBER, acls = RolePermissionAction.CREATE)
    })
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

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMap(__ -> applicationService.findById(application))
                .switchIfEmpty(Maybe.error(new ApplicationNotFoundException(application)))
                // FIXME: propagate organizationId.
                .flatMapSingle(__ -> membershipService.addOrUpdate("DEFAULT", membership, authenticatedUser))
                .map(membership1 -> Response
                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/applications/" + application + "/members/" + membership1.getId()))
                        .entity(membership1)
                        .build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @GET
    @Path("permissions")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List application member's permissions")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Application member's permissions", response = List.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void permissions(@PathParam("domain") String domain,
                            @PathParam("application") String application,
                            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        groupService.findByMember(authenticatedUser.getId())
                .flatMap(groups -> {
                    List<String> members = new ArrayList<>();
                    members.add(authenticatedUser.getId());
                    members.addAll(groups.stream().map(Group::getId).collect(Collectors.toList()));
                    return Observable.fromIterable(members)
                            .flatMapMaybe(member -> {
                                return membershipService.findByReferenceAndMember(application, authenticatedUser.getId())
                                        .flatMap(membership -> roleService.findById(membership.getRole()))
                                        .filter(role -> role.getPermissions() != null)
                                        .map(role -> role.getPermissions().stream().map(perm -> RoleScope.valueOf(role.getScope()).name().toLowerCase() + "_" + perm).collect(Collectors.toList()));
                            })
                            .toList()
                            .map(perms -> perms.stream().flatMap(List::stream).collect(Collectors.toList()));
                })
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{member}")
    public ApplicationMemberResource getMemberResource() {
        return resourceContext.getResource(ApplicationMemberResource.class);
    }

    private Membership convert(NewMembership newMembership) {
        Membership membership = new Membership();
        membership.setMemberId(newMembership.getMemberId());
        membership.setMemberType(newMembership.getMemberType());
        membership.setRole(newMembership.getRole());

        return membership;
    }

}
