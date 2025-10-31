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
package io.gravitee.am.management.handlers.management.api.resources.organizations.tags;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Tag;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.management.service.TagService;
import io.gravitee.am.service.model.NewTag;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@io.swagger.v3.oas.annotations.tags.Tag(name= "sharding-tags")})
public class TagsResource extends AbstractResource {

    @Autowired
    private TagService tagService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listShardingTags",
            summary= "List sharding tags",
            description = "User must have the ORGANIZATION[LIST] permission on the specified organization. " +
            "Each returned tag is filtered and contains only basic information such as id and name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List all the sharding tags",
                    content = @Content(mediaType =  "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = Domain.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @Suspended final AsyncResponse response) {

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_TAG, Acl.LIST)
                .andThen(tagService.findAll(organizationId))
                .map(this::filterTagInfos)
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                .toList()
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createShardingTag",
            summary = "Create a sharding tags",
            description = "User must have the ORGANIZATION_TAG[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sharding tag successfully created"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @Parameter(name = "tag", required = true)
            @Valid @NotNull final NewTag newTag,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_TAG, Acl.CREATE)
                .andThen(tagService.create(newTag, organizationId, authenticatedUser))
                .subscribe(
                        tag -> response.resume(Response
                                .created(URI.create("/organizations/" + organizationId + "/tags/" + tag.getId()))
                                .entity(tag)
                                .build()),
                        response::resume);
    }

    @Path("{tag}")
    public TagResource getTagResource() {
        return resourceContext.getResource(TagResource.class);
    }

    private Tag filterTagInfos(Tag tag) {
        Tag filteredTag = new Tag();
        filteredTag.setId(tag.getId());
        filteredTag.setName(tag.getName());
        filteredTag.setDescription(tag.getDescription());

        return filteredTag;
    }
}
