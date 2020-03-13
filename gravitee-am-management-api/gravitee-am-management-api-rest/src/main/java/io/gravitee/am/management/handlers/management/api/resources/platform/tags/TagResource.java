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
import io.gravitee.am.model.Tag;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.TagService;
import io.gravitee.am.service.exception.TagNotFoundException;
import io.gravitee.am.service.model.UpdateTag;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TagResource extends AbstractResource {

    @Autowired
    private TagService tagService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a sharding tag")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Sharding tag", response = Tag.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_TAG, acls = RolePermissionAction.READ)
    })
    public void get(@PathParam("tag") String tagId, @Suspended final AsyncResponse response) {
        tagService.findById(tagId)
                .switchIfEmpty(Maybe.error(new TagNotFoundException(tagId)))
                .map(tag -> Response.ok(tag).build())
                .subscribe(response::resume, response::resume);
    }


    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update the sharding tag")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Sharding tag successfully updated", response = Tag.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_TAG, acls = RolePermissionAction.UPDATE)
    })
    public void update(
            @ApiParam(name = "tag", required = true) @Valid @NotNull final UpdateTag tagToUpdate,
            @PathParam("tag") String tagId,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        tagService.update(tagId, tagToUpdate, authenticatedUser)
                .subscribe(
                        tag -> response.resume(Response.ok(tag).build()),
                        response::resume);
    }

    @DELETE
    @ApiOperation(value = "Delete the sharding tag")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Sharding tag successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_TAG, acls = RolePermissionAction.DELETE)
    })
    public void delete(@PathParam("tag") String tag,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        tagService.delete(tag, authenticatedUser)
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        response::resume);
    }
}
