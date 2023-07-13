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
import io.gravitee.am.service.model.plugin.FactorPlugin;
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
public class FactorPluginResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetPlugin() {
        final FactorPlugin factorPlugin = new FactorPlugin();
        factorPlugin.setId("res-plugin-id");
        factorPlugin.setName("res-plugin-name");
        factorPlugin.setDescription("res-desc");
        factorPlugin.setVersion("1");

        doReturn(Maybe.just(factorPlugin)).when(factorPluginService).findById("res-plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("factors")
                .path(factorPlugin.getId())
                .request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldGetPlugin_NotFound() {
        final FactorPlugin factorPlugin = new FactorPlugin();
        factorPlugin.setId("res-plugin-id");
        factorPlugin.setName("res-plugin-name");
        factorPlugin.setDescription("res-desc");
        factorPlugin.setVersion("1");

        doReturn(Maybe.empty()).when(factorPluginService).findById("res-plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("factors")
                .path(factorPlugin.getId())
                .request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetPlugin_TechnicalException() {
        doReturn(Maybe.error(new TechnicalManagementException())).when(factorPluginService).findById("res-plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("factors")
                .path("res-plugin-name")
                .path("schema")
                .request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
    @Test
    public void shouldGetSchema() {
        final FactorPlugin factorPlugin = new FactorPlugin();
        factorPlugin.setId("res-plugin-id");
        factorPlugin.setName("res-plugin-name");
        factorPlugin.setDescription("res-desc");
        factorPlugin.setVersion("1");

        doReturn(Maybe.just(factorPlugin)).when(factorPluginService).findById("res-plugin-id");
        doReturn(Maybe.just("{}")).when(factorPluginService).getSchema("res-plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("factors")
                .path(factorPlugin.getId())
                .path("schema")
                .request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldGetSchema_PluginNotFound() {
        final FactorPlugin factorPlugin = new FactorPlugin();
        factorPlugin.setId("res-plugin-id");
        factorPlugin.setName("res-plugin-name");
        factorPlugin.setDescription("res-desc");
        factorPlugin.setVersion("1");

        doReturn(Maybe.empty()).when(factorPluginService).findById("res-plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("factors")
                .path(factorPlugin.getId())
                .path("schema")
                .request().get();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldGetSchema_SchemaNotFound() {
        final FactorPlugin factorPlugin = new FactorPlugin();
        factorPlugin.setId("res-plugin-id");
        factorPlugin.setName("res-plugin-name");
        factorPlugin.setDescription("res-desc");
        factorPlugin.setVersion("1");

        doReturn(Maybe.just(factorPlugin)).when(factorPluginService).findById("res-plugin-id");
        doReturn(Maybe.empty()).when(factorPluginService).getSchema("res-plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("factors")
                .path(factorPlugin.getId())
                .path("schema")
                .request().get();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldGetSchema_TechnicalException() {
        final FactorPlugin factorPlugin = new FactorPlugin();
        factorPlugin.setId("res-plugin-id");
        factorPlugin.setName("res-plugin-name");
        factorPlugin.setDescription("res-desc");
        factorPlugin.setVersion("1");

        doReturn(Maybe.just(factorPlugin)).when(factorPluginService).findById("res-plugin-id");
        doReturn(Maybe.error(new TechnicalManagementException())).when(factorPluginService).getSchema("res-plugin-id");

        final Response response = target("platform")
                .path("plugins")
                .path("factors")
                .path(factorPlugin.getId())
                .path("schema")
                .request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
}
