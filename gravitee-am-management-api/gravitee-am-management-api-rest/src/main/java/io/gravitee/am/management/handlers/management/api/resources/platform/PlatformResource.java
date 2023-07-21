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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.management.handlers.management.api.resources.platform.configuration.ConfigurationResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.plugins.PluginsResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.roles.SystemRoleResource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = "platform")
@Path("/platform")
public class PlatformResource {

    @Context
    private ResourceContext resourceContext;

    @GET
    @Path("/audits/events")
    @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List audit event types",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void list(@Suspended final AsyncResponse response) {
        response.resume(EventType.types());
    }

    @Path("plugins")
    public PluginsResource getPluginsResource() {
        return resourceContext.getResource(PluginsResource.class);
    }

    @Path("roles")
    public SystemRoleResource getSystemRoleResource() {
        return resourceContext.getResource(SystemRoleResource.class);
    }

    @Path("configuration")
    public ConfigurationResource getConfigurationResource() {
        return resourceContext.getResource(ConfigurationResource.class);
    }

    @Path("installation")
    public InstallationResource getInstallationResource() {
        return resourceContext.getResource(InstallationResource.class);
    }

    @Path("license")
    public LicenseResource getLicenseResource() {
        return resourceContext.getResource(LicenseResource.class);
    }
}
