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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.AuthorizationEnginePluginService;
import io.gravitee.am.service.model.plugin.AuthorizationEnginePlugin;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

public class AuthorizationEnginePluginResourceTest extends JerseySpringTest {

    @Autowired
    private AuthorizationEnginePluginService authorizationEnginePluginService;

    @Test
    public void shouldGetAuthorizationEnginePlugin() {
        final String pluginId = "plugin-id";
        final AuthorizationEnginePlugin mockPlugin = new AuthorizationEnginePlugin();
        mockPlugin.setId(pluginId);
        mockPlugin.setName("Test Plugin");

        doReturn(Maybe.just(mockPlugin)).when(authorizationEnginePluginService).findById(pluginId);

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .path(pluginId)
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final AuthorizationEnginePlugin plugin = response.readEntity(AuthorizationEnginePlugin.class);
        assertEquals(pluginId, plugin.getId());
    }

    @Test
    public void shouldGetAuthorizationEnginePlugin_notFound() {
        final String pluginId = "non-existent-plugin";
        doReturn(Maybe.empty()).when(authorizationEnginePluginService).findById(pluginId);

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .path(pluginId)
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetAuthorizationEnginePluginSchema() {
        final String pluginId = "openfga";
        final String schema = "{\"type\":\"authorization-engine\",\"properties\":{\"connectionUri\":{\"type\":\"string\"}}}";

        doReturn(Maybe.just(schema)).when(authorizationEnginePluginService).getSchema(pluginId);

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .path(pluginId)
                .path("schema")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final String responseBody = response.readEntity(String.class);
        assertEquals(schema, responseBody);
    }

    @Test
    public void shouldGetAuthorizationEnginePluginSchema_notFound() {
        final String pluginId = "non-existent-plugin";
        doReturn(Maybe.empty()).when(authorizationEnginePluginService).getSchema(pluginId);

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .path(pluginId)
                .path("schema")
                .request()
                .get();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }
}