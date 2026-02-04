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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags(@Tag(name = "platform"))
@Path("/platform")
public class PlatformResource {

    @Context
    private ResourceContext resourceContext;

    @GET
    @Path("/audits/events")
    @Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listAuditEventTypes",
            summary = "List audit event types",
            description = "There is no particular permission needed. User must be authenticated.")
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

    @Path("capabilities")
    public CapabilitiesResource getCapabilitiesResource() {
        return resourceContext.getResource(CapabilitiesResource.class);
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
