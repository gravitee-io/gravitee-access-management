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

import io.gravitee.am.management.handlers.management.api.resources.platform.plugins.*;
import io.swagger.annotations.Api;

import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin"})
public class PluginsResource {

    @Context
    private ResourceContext resourceContext;

    @Path("identities")
    public IdentityProvidersPluginResource getIdentityProviderPlugins() {
        return resourceContext.getResource(IdentityProvidersPluginResource.class);
    }

    @Path("certificates")
    public CertificatesPluginResource getCertificatePlugins() {
        return resourceContext.getResource(CertificatesPluginResource.class);
    }

    @Path("extensionGrants")
    public ExtensionGrantsPluginResource getTokenGrantersPlugins() {
        return resourceContext.getResource(ExtensionGrantsPluginResource.class);
    }

    @Path("reporters")
    public ReportersPluginResource getReportersPlugins() {
        return resourceContext.getResource(ReportersPluginResource.class);
    }

    @Path("policies")
    public PoliciesPluginResource getPoliciesPlugins() {
        return resourceContext.getResource(PoliciesPluginResource.class);
    }
}
