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

import io.gravitee.am.management.service.IdentityProviderPluginService;
import io.gravitee.am.service.model.plugin.IdentityProviderPlugin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name = "Plugin"), @Tag(name = "Identity Provider")})
public class IdentityProvidersPluginResource {

    public static final String GRAVITEE_AM_IDP = "gravitee-am-idp";
    public static final String SAML2_IDP = "saml2-generic-am-idp";
    @Context
    private ResourceContext resourceContext;

    @Inject
    private IdentityProviderPluginService identityProviderPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listIdentityProviders",
            summary = "List identity provider plugins",
            description = "There is no particular permission needed. User must be authenticated.")
    public void list(@QueryParam("external") Boolean external,
                     @QueryParam("organization") Boolean organization,
                     @QueryParam("expand") List<String> expand,
                     @Suspended final AsyncResponse response) {

        identityProviderPluginService.findAll(external, expand)
                .map(identityProviderPlugins -> identityProviderPlugins.stream()
                        .filter(identityProvider -> !GRAVITEE_AM_IDP.equals(identityProvider.getId()))
                        .filter(identityProvider -> {
                            if (Boolean.TRUE.equals(organization)) {
                                return !SAML2_IDP.equals(identityProvider.getId()); ////SAML2 is currently not available for Organization due to missing CertificateManager In Management API.
                            }
                            return true;
                        })
                        .sorted(Comparator.comparing(IdentityProviderPlugin::getName))
                        .collect(Collectors.toList()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{identity}")
    public IdentityProviderPluginResource getIdentityProviderResource() {
        return resourceContext.getResource(IdentityProviderPluginResource.class);
    }
}
