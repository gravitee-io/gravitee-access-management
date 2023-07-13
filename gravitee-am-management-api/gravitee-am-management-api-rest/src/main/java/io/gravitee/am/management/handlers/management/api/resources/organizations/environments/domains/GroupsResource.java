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
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewGroup;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.*;
import java.util.Collection;
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
import java.util.stream.Collectors;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"group"})
public class GroupsResource extends AbstractResource {

    private static final int MAX_GROUPS_SIZE_PER_PAGE = 100;
    private static final String MAX_GROUPS_SIZE_PER_PAGE_STRING = "100";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private GroupService groupService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "listGroups",
            value = "List groups for a security domain",
            notes = "User must have the DOMAIN_GROUP[LIST] permission on the specified domain " +
                    "or DOMAIN_GROUP[LIST] permission on the specified environment " +
                    "or DOMAIN_GROUP[LIST] permission on the specified organization. " +
                    "Each returned group is filtered and contains only basic information such as id and name.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List groups for a security domain", response = GroupPage.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_GROUPS_SIZE_PER_PAGE_STRING) int size,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_GROUP, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> groupService.findByDomain(domain, page, Integer.min(size, MAX_GROUPS_SIZE_PER_PAGE)))
                        .map(groupPage -> new Page<>(groupPage.getData().stream().map(this::filterGroupInfos).collect(Collectors.toList()), groupPage.getCurrentPage(), groupPage.getTotalCount())))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "createGroup",
            value = "Create a group",
            notes = "User must have the DOMAIN_GROUP[CREATE] permission on the specified domain " +
                    "or DOMAIN_GROUP[CREATE] permission on the specified environment " +
                    "or DOMAIN_GROUP[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Group successfully created", response = Group.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @ApiParam(name = "group", required = true)
            @Valid @NotNull final NewGroup newGroup,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_GROUP, Acl.CREATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(irrelevant -> groupService.create(domain, newGroup, authenticatedUser))
                        .map(group -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/groups/" + group.getId()))
                                .entity(group)
                                .build()))
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

    public static final class GroupPage extends Page<Group> {
        public GroupPage(Collection<Group> data, int currentPage, long totalCount) {
            super(data, currentPage, totalCount);
        }
    }
}