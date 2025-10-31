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
package io.gravitee.am.management.handlers.management.api.resources.organizations.groups;

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupMembersResource extends AbstractResource {

    private static final int MAX_MEMBERS_SIZE_PER_PAGE = 30;
    private static final String MAX_MEMBERS_SIZE_PER_PAGE_STRING = "30";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private OrganizationGroupService orgGroupService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listGroupMembers",
            summary = "List group members",
            description = "User must have the ORGANIZATION_GROUP[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group members successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation =User.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("group") String group,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_MEMBERS_SIZE_PER_PAGE_STRING) int size,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_GROUP, Acl.READ)
                .andThen(orgGroupService.findMembers(organizationId, group, page, Integer.min(size, MAX_MEMBERS_SIZE_PER_PAGE))
                        .flatMap(pagedMembers -> {
                            if (pagedMembers.getData() == null) {
                                return Single.just(pagedMembers);
                            }
                            return Observable.fromIterable(pagedMembers.getData())
                                    .flatMapSingle(member -> {
                                        if (member.getSource() != null) {
                                            return identityProviderService.findById(member.getSource())
                                                    .map(idP -> {
                                                        member.setSource(idP.getName());
                                                        return member;
                                                    })
                                                    .defaultIfEmpty(member);
                                        }
                                        return Single.just(member);
                                    })
                                    .toSortedList(Comparator.comparing(User::getUsername, Comparator.nullsLast(Comparator.naturalOrder())))
                                    .map(members -> new Page<>(members, pagedMembers.getCurrentPage(), pagedMembers.getTotalCount()));
                        }))
                .subscribe(response::resume, response::resume);
    }

    @Path("{member}")
    public GroupMemberResource groupMemberResource() {
        return resourceContext.getResource(GroupMemberResource.class);
    }
}
