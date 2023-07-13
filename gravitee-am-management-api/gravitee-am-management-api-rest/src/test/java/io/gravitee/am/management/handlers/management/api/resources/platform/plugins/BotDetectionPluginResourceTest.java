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
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.BotDetectionPlugin;
import io.gravitee.am.service.model.plugin.ResourcePlugin;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BotDetectionPluginResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetPlugin() {
        final BotDetectionPlugin plugin = new BotDetectionPlugin();
        plugin.setId("plugin-id");
        plugin.setName("plugin-name");
        plugin.setDescription("desc");
        plugin.setVersion("1");

        doReturn(Maybe.just(plugin)).when(botDetectionPluginService).findById("plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("bot-detections")
                .path(plugin.getId())
                .request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldGetPlugin_NotFound() {
        final BotDetectionPlugin plugin = new BotDetectionPlugin();
        plugin.setId("plugin-id");

        doReturn(Maybe.empty()).when(botDetectionPluginService).findById("plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("bot-detections")
                .path(plugin.getId())
                .request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetPlugin_TechnicalException() {
        doReturn(Maybe.error(new TechnicalManagementException())).when(botDetectionPluginService).findById("plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("bot-detections")
                .path("plugin-name")
                .path("schema")
                .request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
    @Test
    public void shouldGetSchema() {
        final BotDetectionPlugin plugin = new BotDetectionPlugin();
        plugin.setId("plugin-id");
        plugin.setName("plugin-name");
        plugin.setDescription("desc");
        plugin.setVersion("1");

        doReturn(Maybe.just(plugin)).when(botDetectionPluginService).findById("plugin-id");
        doReturn(Maybe.just("{}")).when(botDetectionPluginService).getSchema("plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("bot-detections")
                .path(plugin.getId())
                .path("schema")
                .request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldGetSchema_PluginNotFound() {
        final BotDetectionPlugin plugin = new BotDetectionPlugin();
        plugin.setId("plugin-id");
        plugin.setName("plugin-name");
        plugin.setDescription("desc");
        plugin.setVersion("1");

        doReturn(Maybe.empty()).when(botDetectionPluginService).findById("plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("bot-detections")
                .path(plugin.getId())
                .path("schema")
                .request().get();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldGetSchema_SchemaNotFound() {
        final BotDetectionPlugin plugin = new BotDetectionPlugin();
        plugin.setId("plugin-id");
        plugin.setName("plugin-name");
        plugin.setDescription("desc");
        plugin.setVersion("1");

        doReturn(Maybe.just(plugin)).when(botDetectionPluginService).findById("plugin-id");
        doReturn(Maybe.empty()).when(botDetectionPluginService).getSchema("plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("bot-detections")
                .path(plugin.getId())
                .path("schema")
                .request().get();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldGetSchema_TechnicalException() {
        final BotDetectionPlugin plugin = new BotDetectionPlugin();
        plugin.setId("plugin-id");
        plugin.setName("plugin-name");
        plugin.setDescription("desc");
        plugin.setVersion("1");

        doReturn(Maybe.just(plugin)).when(botDetectionPluginService).findById("plugin-id");
        doReturn(Maybe.error(new TechnicalManagementException())).when(botDetectionPluginService).getSchema("plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("bot-detections")
                .path(plugin.getId())
                .path("schema")
                .request().get();

        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
}
