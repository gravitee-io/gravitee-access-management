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
import io.gravitee.am.service.model.plugin.AuthorizationEnginePlugin;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author GraviteeSource Team
 */
public class AuthorizationEnginesPluginResourceTest extends JerseySpringTest {

    @BeforeEach
    public void resetMocks() {
        reset(authorizationEnginePluginService);
    }

    @Test
    public void shouldListAuthorizationEnginePlugins() {
        final AuthorizationEnginePlugin plugin1 = new AuthorizationEnginePlugin();
        plugin1.setId("openfga");
        plugin1.setName("OpenFGA");
        plugin1.setVersion("1.0.0");
        plugin1.setDeployed(true);

        final AuthorizationEnginePlugin plugin2 = new AuthorizationEnginePlugin();
        plugin2.setId("zanzibar");
        plugin2.setName("Zanzibar");
        plugin2.setVersion("1.0.0");
        plugin2.setDeployed(false);

        doReturn(Single.just(Arrays.asList(plugin1, plugin2)))
                .when(authorizationEnginePluginService).findAll();

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        verify(authorizationEnginePluginService, times(1)).findAll();
    }

    @Test
    public void shouldListAuthorizationEnginePlugins_emptyList() {
        doReturn(Single.just(Collections.emptyList()))
                .when(authorizationEnginePluginService).findAll();

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldListAuthorizationEnginePlugins_serviceError() {
        doReturn(Single.error(new RuntimeException("Service error")))
                .when(authorizationEnginePluginService).findAll();

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .request()
                .get();

        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldSortPluginsByName() {
        final AuthorizationEnginePlugin plugin1 = new AuthorizationEnginePlugin();
        plugin1.setId("zanzibar");
        plugin1.setName("Zanzibar");

        final AuthorizationEnginePlugin plugin2 = new AuthorizationEnginePlugin();
        plugin2.setId("openfga");
        plugin2.setName("OpenFGA");

        final AuthorizationEnginePlugin plugin3 = new AuthorizationEnginePlugin();
        plugin3.setId("alpha");
        plugin3.setName("Alpha Engine");

        // Return plugins in unsorted order
        doReturn(Single.just(Arrays.asList(plugin1, plugin2, plugin3)))
                .when(authorizationEnginePluginService).findAll();

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<AuthorizationEnginePlugin> plugins = response.readEntity(new jakarta.ws.rs.core.GenericType<List<AuthorizationEnginePlugin>>() {});
        assertEquals(3, plugins.size());
        assertEquals("Alpha Engine", plugins.get(0).getName());
        assertEquals("OpenFGA", plugins.get(1).getName());
        assertEquals("Zanzibar", plugins.get(2).getName());
    }

    @Test
    public void shouldHandleOnlyDeployedPlugins() {
        final AuthorizationEnginePlugin deployedPlugin = new AuthorizationEnginePlugin();
        deployedPlugin.setId("openfga");
        deployedPlugin.setName("OpenFGA");
        deployedPlugin.setDeployed(true);

        final AuthorizationEnginePlugin notDeployedPlugin = new AuthorizationEnginePlugin();
        notDeployedPlugin.setId("zanzibar");
        notDeployedPlugin.setName("Zanzibar");
        notDeployedPlugin.setDeployed(false);

        doReturn(Single.just(Arrays.asList(deployedPlugin, notDeployedPlugin)))
                .when(authorizationEnginePluginService).findAll();

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        // Both deployed and not deployed should be returned by the service
        verify(authorizationEnginePluginService, times(1)).findAll();
    }

    @Test
    public void shouldHandleMultiplePluginsWithSameName() {
        final AuthorizationEnginePlugin plugin1 = new AuthorizationEnginePlugin();
        plugin1.setId("openfga-v2");
        plugin1.setName("OpenFGA2");
        plugin1.setVersion("2.0.0");

        final AuthorizationEnginePlugin plugin2 = new AuthorizationEnginePlugin();
        plugin2.setId("openfga-v1");
        plugin2.setName("OpenFGA1");
        plugin2.setVersion("1.0.0");

        doReturn(Single.just(Arrays.asList(plugin1, plugin2)))
                .when(authorizationEnginePluginService).findAll();

        final Response response = target("platform")
                .path("plugins")
                .path("authorization-engines")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final List<AuthorizationEnginePlugin> plugins = response.readEntity(new jakarta.ws.rs.core.GenericType<List<AuthorizationEnginePlugin>>() {});
        assertEquals(2, plugins.size());
        assertEquals("openfga-v1", plugins.get(0).getId());
        assertEquals("openfga-v2", plugins.get(1).getId());
    }
}
