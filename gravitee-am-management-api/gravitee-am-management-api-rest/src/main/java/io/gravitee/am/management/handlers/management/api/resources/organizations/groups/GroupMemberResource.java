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
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.service.exception.MemberAlreadyExistsException;
import io.gravitee.am.service.exception.MemberNotFoundException;
import io.gravitee.am.service.model.UpdateGroup;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupMemberResource extends AbstractResource {

    @Autowired
    private OrganizationGroupService orgGroupService;

    @Autowired
    private OrganizationUserService userService;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "addGroupMember",
            summary = "Add a group member",
            description = "User must have the ORGANIZATION_GROUP[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Member has been added successfully"),
            @ApiResponse(responseCode = "400", description = "User does not exist"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void addMember(
            @PathParam("organizationId") String organizationId,
            @PathParam("group") String group,
            @PathParam("member") String userId,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_GROUP, Acl.UPDATE)
                .andThen(orgGroupService.findById(organizationId, group)
                        .flatMap(group1 -> userService.findById(ReferenceType.ORGANIZATION, organizationId, userId)
                                .flatMap(user -> {
                                    if (group1.getMembers() != null && group1.getMembers().contains(userId)) {
                                        return Single.error(new MemberAlreadyExistsException(userId));
                                    }

                                    List<String> groupMembers = group1.getMembers() != null ? new ArrayList(group1.getMembers()) : new ArrayList();
                                    groupMembers.add(userId);

                                    UpdateGroup updateGroup = new UpdateGroup();
                                    updateGroup.setName(group1.getName());
                                    updateGroup.setDescription(group1.getDescription());
                                    updateGroup.setRoles(group1.getRoles());
                                    updateGroup.setMembers(groupMembers);
                                    return orgGroupService.update(organizationId, group, updateGroup, authenticatedUser);
                                })))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "removeGroupMember",
            summary = "Remove a group member",
            description = "User must have the ORGANIZATION_GROUP[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Member has been removed successfully"),
            @ApiResponse(responseCode = "400", description = "User does not exist"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void removeMember(
            @PathParam("organizationId") String organizationId,
            @PathParam("group") String group,
            @PathParam("member") String userId,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_GROUP, Acl.UPDATE)
                .andThen(orgGroupService.findById(organizationId, group)
                        .flatMap(group1 -> userService.findById(ReferenceType.ORGANIZATION, organizationId, userId)
                                .flatMap(user -> {
                                    if (group1.getMembers() == null || !group1.getMembers().contains(userId)) {
                                        return Single.error(new MemberNotFoundException(userId));
                                    }

                                    List<String> groupMembers = group1.getMembers() != null ? new ArrayList(group1.getMembers()) : new ArrayList();
                                    groupMembers.remove(userId);

                                    UpdateGroup updateGroup = new UpdateGroup();
                                    updateGroup.setName(group1.getName());
                                    updateGroup.setDescription(group1.getDescription());
                                    updateGroup.setRoles(group1.getRoles());
                                    updateGroup.setMembers(groupMembers);
                                    return orgGroupService.update(organizationId, group, updateGroup, authenticatedUser);
                                })))
                .subscribe(response::resume, response::resume);
    }
}
