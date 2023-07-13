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
package io.gravitee.am.management.handlers.management.api.resources.organizations.members;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.MembershipListItem;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.model.NewMembership;
import io.gravitee.common.http.MediaType;
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

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MembersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private MembershipService membershipService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List members for an organization",
            notes = "User must have ORGANIZATION_MEMBER[LIST] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List members for an organization", response = MembershipListItem.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void getMembers(
            @PathParam("organizationId") String organizationId,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_MEMBER, Acl.LIST)
                .andThen(organizationService.findById(organizationId)
                        .flatMap(organization -> membershipService.findByReference(organization.getId(), ReferenceType.ORGANIZATION).toList())
                        .flatMap(memberships -> membershipService.getMetadata(memberships).map(metadata -> new MembershipListItem(memberships, metadata))))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add or update an organization member",
            notes = "User must have ORGANIZATION_MEMBER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Member has been added or updated successfully"),
            @ApiResponse(code = 400, message = "Membership parameter is not valid"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void addOrUpdateMember(
            @PathParam("organizationId") String organizationId,
            @Valid @NotNull NewMembership newMembership,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        final Membership membership = convert(newMembership);
        membership.setReferenceId(organizationId);
        membership.setReferenceType(ReferenceType.ORGANIZATION);

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_MEMBER, Acl.CREATE)
                .andThen(organizationService.findById(organizationId)
                        .flatMap(organization -> membershipService.addOrUpdate(organizationId, membership, authenticatedUser))
                        .map(membership1 -> Response
                                .created(URI.create("/organizations/" + organizationId + "/members/" + membership1.getId()))
                                .entity(membership1)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{member}")
    public MemberResource getMemberResource() {
        return resourceContext.getResource(MemberResource.class);
    }

    private Membership convert(NewMembership newMembership) {
        Membership membership = new Membership();
        membership.setMemberId(newMembership.getMemberId());
        membership.setMemberType(newMembership.getMemberType());
        membership.setRoleId(newMembership.getRole());

        return membership;
    }
}
