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
package io.gravitee.am.management.service.impl.plugins;

import io.gravitee.am.authorizationengine.api.AuthorizationEngine;
import io.gravitee.am.plugins.authorizationengine.core.AuthorizationEnginePluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.AuthorizationEnginePlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginManifest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationEnginePluginServiceImplTest {

    @Mock
    private AuthorizationEnginePluginManager authorizationEnginePluginManager;

    @InjectMocks
    private AuthorizationEnginePluginServiceImpl service;

    private Plugin mockPlugin;
    private PluginManifest mockManifest;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(Plugin.class);
        mockManifest = mock(PluginManifest.class);
    }

    @Test
    void shouldFindAllPlugins() {
        // given
        AuthorizationEngine authEngine1 = mock(AuthorizationEngine.class);
        PluginManifest manifest1 = mock(PluginManifest.class);
        when(authEngine1.manifest()).thenReturn(manifest1);
        when(manifest1.id()).thenReturn("openfga");
        when(manifest1.name()).thenReturn("OpenFGA");
        when(manifest1.description()).thenReturn("OpenFGA Authorization Engine");
        when(manifest1.version()).thenReturn("1.0.0");
        when(manifest1.feature()).thenReturn("authorization-engine");
        when(authEngine1.deployed()).thenReturn(true);

        AuthorizationEngine authEngine2 = mock(AuthorizationEngine.class);
        PluginManifest manifest2 = mock(PluginManifest.class);
        when(authEngine2.manifest()).thenReturn(manifest2);
        when(manifest2.id()).thenReturn("zanzibar");
        when(manifest2.name()).thenReturn("Zanzibar");
        when(manifest2.description()).thenReturn("Google Zanzibar Authorization Engine");
        when(manifest2.version()).thenReturn("1.0.0");
        when(manifest2.feature()).thenReturn("authorization-engine");
        when(authEngine2.deployed()).thenReturn(false);

        doReturn(Arrays.asList(authEngine1, authEngine2)).when(authorizationEnginePluginManager).findAll(true);

        // when
        TestObserver<List<AuthorizationEnginePlugin>> observer = service.findAll().test();

        // then
        observer.assertComplete();
        observer.assertValue(result -> {
            assertEquals(2, result.size());

            AuthorizationEnginePlugin p1 = result.get(0);
            assertEquals("openfga", p1.getId());
            assertEquals("OpenFGA", p1.getName());
            assertEquals("OpenFGA Authorization Engine", p1.getDescription());
            assertEquals("1.0.0", p1.getVersion());
            assertTrue(p1.isDeployed());

            AuthorizationEnginePlugin p2 = result.get(1);
            assertEquals("zanzibar", p2.getId());
            assertFalse(p2.isDeployed());

            return true;
        });

        verify(authorizationEnginePluginManager, times(1)).findAll(true);
    }


    @Test
    void shouldFindAllPlugins_emptyList() {
        // given
        when(authorizationEnginePluginManager.findAll(true)).thenReturn(Collections.emptyList());

        // when
        TestObserver<List<AuthorizationEnginePlugin>> observer = service.findAll().test();

        // then
        observer.assertComplete();
        observer.assertValue(result -> {
            assertTrue(result.isEmpty());
            return true;
        });
    }

    @Test
    void shouldFindPluginById() {
        // given
        String pluginId = "openfga";

        when(mockPlugin.manifest()).thenReturn(mockManifest);
        when(mockManifest.id()).thenReturn("openfga");
        when(mockManifest.name()).thenReturn("OpenFGA");
        when(mockManifest.description()).thenReturn("OpenFGA Authorization Engine");
        when(mockManifest.version()).thenReturn("1.0.0");
        when(mockManifest.feature()).thenReturn("authorization-engine");
        when(mockPlugin.deployed()).thenReturn(true);

        when(authorizationEnginePluginManager.findById(pluginId)).thenReturn(mockPlugin);

        // when
        TestObserver<AuthorizationEnginePlugin> observer = service.findById(pluginId).test();

        // then
        observer.assertComplete();
        observer.assertValue(plugin -> {
            assertEquals("openfga", plugin.getId());
            assertEquals("OpenFGA", plugin.getName());
            assertEquals("OpenFGA Authorization Engine", plugin.getDescription());
            assertEquals("1.0.0", plugin.getVersion());
            assertTrue(plugin.isDeployed());
            return true;
        });

        verify(authorizationEnginePluginManager, times(1)).findById(pluginId);
    }

    @Test
    void shouldReturnEmptyWhenPluginNotFound() {
        // given
        String pluginId = "non-existent";

        when(authorizationEnginePluginManager.findById(pluginId)).thenReturn(null);

        // when
        TestObserver<AuthorizationEnginePlugin> observer = service.findById(pluginId).test();

        // then
        observer.assertComplete();
        observer.assertNoValues();

        verify(authorizationEnginePluginManager, times(1)).findById(pluginId);
    }

    @Test
    void shouldHandleErrorWhenFindingById() {
        // given
        String pluginId = "error-plugin";

        when(authorizationEnginePluginManager.findById(pluginId))
                .thenThrow(new RuntimeException("Plugin manager error"));

        // when
        TestObserver<AuthorizationEnginePlugin> observer = service.findById(pluginId).test();

        // then
        observer.assertError(TechnicalManagementException.class);
        observer.assertError(ex -> ex.getMessage().contains("An error occurs while trying to get authorization engine plugin : error-plugin"));
    }

    @Test
    void shouldGetSchema() throws Exception {
        // given
        String pluginId = "openfga";
        String schema = "{\"type\":\"object\",\"properties\":{\"connectionUri\":{\"type\":\"string\"}}}";

        when(authorizationEnginePluginManager.getSchema(pluginId)).thenReturn(schema);

        // when
        TestObserver<String> observer = service.getSchema(pluginId).test();

        // then
        observer.assertComplete();
        observer.assertValue(schema);

        verify(authorizationEnginePluginManager, times(1)).getSchema(pluginId);
    }

    @Test
    void shouldReturnEmptyWhenSchemaNotFound() throws Exception {
        // given
        String pluginId = "no-schema";

        when(authorizationEnginePluginManager.getSchema(pluginId)).thenReturn(null);

        // when
        TestObserver<String> observer = service.getSchema(pluginId).test();

        // then
        observer.assertComplete();
        observer.assertNoValues();
    }

    @Test
    void shouldHandleErrorWhenGettingSchema() throws Exception {
        // given
        String pluginId = "error-plugin";

        when(authorizationEnginePluginManager.getSchema(pluginId))
                .thenThrow(new RuntimeException("Schema retrieval error"));

        // when
        TestObserver<String> observer = service.getSchema(pluginId).test();

        // then
        observer.assertError(TechnicalManagementException.class);
        observer.assertError(ex -> ex.getMessage().contains("An error occurs while trying to get schema for authorization engine plugin error-plugin"));
    }
}
