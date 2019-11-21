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
package io.gravitee.am.management.handlers.management.api.resources.platform.tags;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.TagService;
import io.gravitee.am.service.model.NewTag;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.*;
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
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Path("/tags")
@Api(tags = {"sharding-tags"})
public class TagsResource extends AbstractResource {

    @Autowired
    private TagService tagService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "List sharding tags",
            notes = "List all the sharding tags.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List all the sharding tags", response = Domain.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@Suspended final AsyncResponse response) {
         tagService.findAll()
                 .map(domains ->
                        domains.stream()
                                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                                .collect(Collectors.toList()))
                .subscribe(
                        result -> response.resume(Response.ok(result).build()),
                        response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a sharding tags")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Sharding tag successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_TAG, acls = RolePermissionAction.CREATE)
    })
    public void create(
            @ApiParam(name = "tag", required = true)
            @Valid @NotNull final NewTag newTag,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        tagService.create(newTag, authenticatedUser)
                .subscribe(
                        tag -> response.resume(Response
                                                    .created(URI.create("/tags/" + tag.getId()))
                                                    .entity(tag)
                                                    .build()),
                        response::resume);
    }

    @Path("{tag}")
    public TagResource getTagResource() {
        return resourceContext.getResource(TagResource.class);
    }

}
