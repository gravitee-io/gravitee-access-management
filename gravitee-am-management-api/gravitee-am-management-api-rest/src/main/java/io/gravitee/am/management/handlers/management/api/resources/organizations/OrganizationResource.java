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
package io.gravitee.am.management.handlers.management.api.resources.organizations;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.audits.AuditsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.environments.EnvironmentsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.forms.FormsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.groups.GroupsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.idps.IdentityProvidersResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.members.MembersResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.roles.RolesResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.search.SearchResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.settings.SettingsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.tags.TagsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.users.UsersResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.plugins.PluginsResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.model.NewOrganization;
import io.gravitee.common.http.MediaType;
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

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OrganizationResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private OrganizationService organizationService;

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create or update an organization",
            notes = "User must have the ORGANIZATION[CREATE] permission on the platform")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Organization successfully created or updated"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @ApiParam(name = "organization", required = true)
            @Valid @NotNull final NewOrganization newOrganization,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkPermission(ReferenceType.PLATFORM, Platform.DEFAULT, Permission.ORGANIZATION, Acl.CREATE)
                .andThen(organizationService.createOrUpdate(organizationId, newOrganization, authenticatedUser))
                .subscribe(response::resume, response::resume);
    }

    @Path("environments")
    public EnvironmentsResource getEnvironmentsResource() {
        return resourceContext.getResource(EnvironmentsResource.class);
    }

    @Path("plugins")
    public PluginsResource getPluginsResource() {
        return resourceContext.getResource(PluginsResource.class);
    }

    @Path("audits")
    public AuditsResource getAuditsResource() {
        return resourceContext.getResource(AuditsResource.class);
    }

    @Path("members")
    public MembersResource getMembersResource() {
        return resourceContext.getResource(MembersResource.class);
    }

    @Path("tags")
    public TagsResource getTagsResource() {
        return resourceContext.getResource(TagsResource.class);
    }

    @Path("roles")
    public RolesResource getRolesResource() {
        return resourceContext.getResource(RolesResource.class);
    }

    @Path("groups")
    public GroupsResource getGroupsResource() {
        return resourceContext.getResource(GroupsResource.class);
    }

    @Path("identities")
    public IdentityProvidersResource getIdentityProvidersResource() {
        return resourceContext.getResource(IdentityProvidersResource.class);
    }

    @Path("users")
    public UsersResource getUsersResource() {
        return resourceContext.getResource(UsersResource.class);
    }

    @Path("settings")
    public SettingsResource getSettingsResource() {
        return resourceContext.getResource(SettingsResource.class);
    }

    @Path("forms")
    public FormsResource getFormsResource() {
        return resourceContext.getResource(FormsResource.class);
    }

    @Path("search")
    public SearchResource getSearchResource() {
        return resourceContext.getResource(SearchResource.class);
    }
}