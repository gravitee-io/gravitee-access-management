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

import io.gravitee.am.management.handlers.management.api.model.PlatformCapabilities;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.plugins.authenticator.core.AuthenticatorPluginManager;
import io.gravitee.common.http.MediaType;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

public class CapabilitiesResource extends AbstractResource {

    private static final String MAGIC_LINK_AUTHENTICATOR_PLUGIN_ID = "magiclink-am-authenticator";

    @Autowired
    private AuthenticatorPluginManager authenticatorPluginManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCapabilities() {
        boolean deployed = authenticatorPluginManager.isPluginDeployed(MAGIC_LINK_AUTHENTICATOR_PLUGIN_ID);
        return Response.ok(new PlatformCapabilities(deployed)).build();
    }
}
