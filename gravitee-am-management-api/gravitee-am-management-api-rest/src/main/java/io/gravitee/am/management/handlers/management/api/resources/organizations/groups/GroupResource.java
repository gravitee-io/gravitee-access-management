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
import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.model.UpdateGroup;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private OrganizationGroupService orgGroupService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a platform group",
            operationId = "getOrganizationGroup",
            description = "User must have the ORGANIZATION_GROUP[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation =Group.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("group") String group,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_GROUP, Acl.READ)
                .andThen(orgGroupService.findById(organizationId, group))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a platform group",
            operationId = "updateOrganizationGroup",
            description = "User must have the ORGANIZATION_GROUP[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Group successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation =User.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void updateGroup(
            @PathParam("organizationId") String organizationId,
            @PathParam("group") String group,
            @Parameter(name = "group", required = true) @Valid @NotNull UpdateGroup updateGroup,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_GROUP, Acl.UPDATE)
                .andThen(orgGroupService.update(organizationId, group, updateGroup, authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(summary = "Delete a platform group",
            operationId = "deleteOrganizationGroup",
            description = "User must have the ORGANIZATION_GROUP[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Group successfully deleted"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("group") String group,
            @Suspended final AsyncResponse response) {
        final io.gravitee.am.identityprovider.api.User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_GROUP, Acl.DELETE)
                .andThen(orgGroupService.delete(organizationId, group, authenticatedUser))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @Path("members")
    @Operation(summary = "Get members of a platform group",
            operationId = "getOrganizationGroupMembers")
    public GroupMembersResource getGroupMembersResource() {
        return resourceContext.getResource(GroupMembersResource.class);
    }
}
