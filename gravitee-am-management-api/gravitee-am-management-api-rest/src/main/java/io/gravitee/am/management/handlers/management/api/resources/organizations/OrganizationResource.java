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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.audits.AuditsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.entrypoints.EntrypointsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.environments.EnvironmentsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.forms.FormsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.groups.GroupsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.idps.IdentityProvidersResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.members.MembersResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.roles.RolesResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.settings.SettingsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.tags.TagsResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.users.OrganizationUsersResource;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "organization")
public class OrganizationResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Path("environments")
    @Operation(operationId = "getEnvironmentsResource", summary = "Get environments resource")
    public EnvironmentsResource getEnvironmentsResource() {
        return resourceContext.getResource(EnvironmentsResource.class);
    }

    @Path("audits")
    @Operation(operationId = "getAuditsResource", summary = "Get audits resource")
    public AuditsResource getAuditsResource() {
        return resourceContext.getResource(AuditsResource.class);
    }

    @Path("reporters")
    @Operation(operationId = "getReporters", summary = "Get reporters resource")
    public ReportersResource getReporters() {
        return resourceContext.getResource(ReportersResource.class);
    }

    @Path("members")
    @Operation(operationId = "getMembersResource", summary = "Get members resource")
    public MembersResource getMembersResource() {
        return resourceContext.getResource(MembersResource.class);
    }

    @Path("tags")
    @Operation(operationId = "getTagsResource", summary = "Get tags resource")
    public TagsResource getTagsResource() {
        return resourceContext.getResource(TagsResource.class);
    }

    @Path("entrypoints")
    @Operation(operationId = "getEntrypointsResource", summary = "Get entrypoints resource")
    public EntrypointsResource getEntrypointsResource() {
        return resourceContext.getResource(EntrypointsResource.class);
    }

    @Path("roles")
    @Operation(operationId = "getRolesResource", summary = "Get roles resource")
    public RolesResource getRolesResource() {
        return resourceContext.getResource(RolesResource.class);
    }

    @Path("groups")
    @Operation(operationId = "getGroupsResource", summary = "Get groups resource")
    public GroupsResource getGroupsResource() {
        return resourceContext.getResource(GroupsResource.class);
    }

    @Path("identities")
    @Operation(operationId = "getIdentityProvidersResource", summary = "Get identity providers resource")
    public IdentityProvidersResource getIdentityProvidersResource() {
        return resourceContext.getResource(IdentityProvidersResource.class);
    }

    @Path("users")
    @Operation(operationId = "getUsersResource", summary = "Get users resource")
    public OrganizationUsersResource getUsersResource() {
        return resourceContext.getResource(OrganizationUsersResource.class);
    }

    @Path("settings")
    @Operation(operationId = "getSettingsResource", summary = "Get settings resource")
    public SettingsResource getSettingsResource() {
        return resourceContext.getResource(SettingsResource.class);
    }

    @Path("forms")
    @Operation(operationId = "getFormsResource", summary = "Get forms resource")
    public FormsResource getFormsResource() {
        return resourceContext.getResource(FormsResource.class);
    }
}
