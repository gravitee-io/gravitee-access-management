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
package io.gravitee.am.gateway.handler.management.api.resources.platform.plugins;

import io.gravitee.am.gateway.service.IdentityProviderPluginService;
import io.gravitee.am.gateway.service.model.plugin.IdentityProviderPlugin;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Defines the REST resources to manage {@link io.gravitee.am.model.IdentityProvider}.
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Path("/policies")
@Api(tags = {"Platform", "Plugin", "Identity Provider"})
public class IdentityProvidersPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private IdentityProviderPluginService identityProviderPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List identity providers")
    public Collection<IdentityProviderPlugin> listPolicies() {
        return identityProviderPluginService.findAll()
                .stream()
                .sorted(Comparator.comparing(IdentityProviderPlugin::getName))
                .collect(Collectors.toList());
    }

    @Path("{identity}")
    public IdentityProviderPluginResource getIdentityProviderResource() {
        return resourceContext.getResource(IdentityProviderPluginResource.class);
    }

    /*
    private PolicyListItem convert(PolicyEntity policy) {
        PolicyListItem item = new PolicyListItem();

        item.setId(policy.getId());
        item.setName(policy.getName());
        item.setDescription(policy.getDescription());
        item.setVersion(policy.getVersion());
        item.setType(policy.getType());

        return item;
    }
    */
}
