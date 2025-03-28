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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains.GroupsResource.GroupPage;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.model.NewGroup;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "group")
public class GroupsResource extends AbstractResource {

    private static final int MAX_GROUPS_SIZE_PER_PAGE = 100;
    private static final String MAX_GROUPS_SIZE_PER_PAGE_STRING = "100";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private OrganizationGroupService orgGroupService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List groups of the organization",
            description = "User must have the ORGANIZATION[LIST] permission on the specified organization. " +
                    "Each returned group is filtered and contains only basic information such as id and name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List groups of the organization",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = Group.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_GROUPS_SIZE_PER_PAGE_STRING) int size,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_GROUP, Acl.LIST)
                .andThen(orgGroupService.findAll(organizationId, page, Integer.min(size, MAX_GROUPS_SIZE_PER_PAGE))
                        .map(groupPage ->
                                new GroupPage(groupPage.getData().stream().map(this::filterGroupInfos).collect(Collectors.toList()), groupPage.getCurrentPage(), groupPage.getTotalCount())))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a platform group",
            description = "User must have the ORGANIZATION_GROUP[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Group successfully created"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @Parameter(name = "group", required = true) @Valid @NotNull final NewGroup newGroup,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_GROUP, Acl.CREATE)
                .andThen(orgGroupService.create(organizationId, newGroup, authenticatedUser)
                        .map(group -> Response.created(URI.create("/organizations/" + organizationId + "/groups/" + group.getId()))
                                .entity(group).build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{group}")
    public GroupResource getGroupResource() {
        return resourceContext.getResource(GroupResource.class);
    }

    private Group filterGroupInfos(Group group) {
        Group filteredGroup = new Group();
        filteredGroup.setId(group.getId());
        filteredGroup.setName(group.getName());
        filteredGroup.setDescription(group.getDescription());

        return filteredGroup;
    }
}
