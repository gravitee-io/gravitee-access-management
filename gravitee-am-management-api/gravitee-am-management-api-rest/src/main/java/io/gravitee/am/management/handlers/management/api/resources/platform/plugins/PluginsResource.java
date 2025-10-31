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
package io.gravitee.am.management.handlers.management.api.resources.platform.plugins;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "Plugin")})
public class PluginsResource {

    @Context
    private ResourceContext resourceContext;

    @Path("identities")
    @Operation(operationId = "getIdentityProviderPlugins", summary = "Get identity provider plugins")
    public IdentityProvidersPluginResource getIdentityProviderPlugins() {
        return resourceContext.getResource(IdentityProvidersPluginResource.class);
    }

    @Path("certificates")
    @Operation(operationId = "getCertificatePlugins", summary = "Get certificate plugins")
    public CertificatesPluginResource getCertificatePlugins() {
        return resourceContext.getResource(CertificatesPluginResource.class);
    }

    @Path("extensionGrants")
    @Operation(operationId = "getTokenGrantersPlugins", summary = "Get extension grant plugins")
    public ExtensionGrantsPluginResource getTokenGrantersPlugins() {
        return resourceContext.getResource(ExtensionGrantsPluginResource.class);
    }

    @Path("reporters")
    @Operation(operationId = "getReportersPlugins", summary = "Get reporter plugins")
    public ReportersPluginResource getReportersPlugins() {
        return resourceContext.getResource(ReportersPluginResource.class);
    }

    @Path("policies")
    @Operation(operationId = "getPoliciesPlugins", summary = "Get policy plugins")
    public PoliciesPluginResource getPoliciesPlugins() {
        return resourceContext.getResource(PoliciesPluginResource.class);
    }

    @Path("factors")
    @Operation(operationId = "getFactorsPlugins", summary = "Get factor plugins")
    public FactorsPluginResource getFactorsPlugins() {
        return resourceContext.getResource(FactorsPluginResource.class);
    }

    @Path("resources")
    @Operation(operationId = "getResourcesPlugins", summary = "Get resource plugins")
    public ResourcesPluginResource getResourcesPlugins() {
        return resourceContext.getResource(ResourcesPluginResource.class);
    }

    @Path("notifiers")
    @Operation(operationId = "getNotifiersPluginResource", summary = "Get notifier plugins")
    public NotifiersPluginResource getNotifiersPluginResource() {
        return resourceContext.getResource(NotifiersPluginResource.class);
    }

    @Path("bot-detections")
    @Operation(operationId = "getBotDetectionsPlugins", summary = "Get bot detection plugins")
    public BotDetectionsPluginResource getBotDetectionsPlugins() {
        return resourceContext.getResource(BotDetectionsPluginResource.class);
    }

    @Path("auth-device-notifiers")
    @Operation(operationId = "getAuthenticationDeviceNotifiersPluginsResource", summary = "Get authentication device notifier plugins")
    public AuthenticationDeviceNotifiersPluginResource getAuthenticationDeviceNotifiersPluginsResource() {
        return resourceContext.getResource(AuthenticationDeviceNotifiersPluginResource.class);
    }

    @Path("device-identifiers")
    @Operation(operationId = "getDeviceIdentifiersPlugins", summary = "Get device identifier plugins")
    public DeviceIdentifiersPluginResource getDeviceIdentifiersPlugins() {
        return resourceContext.getResource(DeviceIdentifiersPluginResource.class);
    }

    @Path("authorization-engines")
    @Operation(operationId = "getAuthorizationEnginesPlugins", summary = "Get authorization engine plugins")
    public AuthorizationEnginesPluginResource getAuthorizationEnginesPlugins() {
        return resourceContext.getResource(AuthorizationEnginesPluginResource.class);
    }
}