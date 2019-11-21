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
package io.gravitee.am.management.handlers.management.api.resources.platform;

import io.gravitee.am.management.handlers.management.api.resources.platform.audits.AuditsResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.forms.FormsResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.groups.GroupsResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.idps.IdentityProvidersResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.plugins.PluginsResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.reporters.ReportersResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.roles.RolesResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.search.SearchResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.settings.SettingsResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.tags.TagsResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.users.UsersResource;

import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Path("/platform")
public class PlatformResource {

    @Context
    private ResourceContext resourceContext;

    @Path("plugins")
    public PluginsResource getPluginsResource() {
        return resourceContext.getResource(PluginsResource.class);
    }

    @Path("audits")
    public AuditsResource getAuditsResource() {
        return resourceContext.getResource(AuditsResource.class);
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

    @Path("reporters")
    public ReportersResource getReportersResource() {
        return resourceContext.getResource(ReportersResource.class);
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
