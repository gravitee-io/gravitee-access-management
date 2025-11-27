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
package io.gravitee.am.service.secrets.testsupport;

import io.gravitee.node.secrets.plugin.mock.MockSecretProviderFactory;
import io.gravitee.node.secrets.plugin.mock.conf.MockSecretProviderConfiguration;
import io.gravitee.node.secrets.plugins.SecretProviderPlugin;
import io.gravitee.node.secrets.plugins.SecretProviderPluginManager;
import io.gravitee.node.secrets.plugins.internal.DefaultSecretProviderClassLoaderFactory;
import io.gravitee.node.secrets.plugins.internal.DefaultSecretProviderPlugin;
import io.gravitee.node.secrets.plugins.internal.DefaultSecretProviderPluginManager;
import io.gravitee.plugin.core.api.PluginManifest;
import io.gravitee.secrets.api.plugin.SecretProvider;

import java.net.URL;
import java.nio.file.Path;

/**
 * @author GraviteeSource Team
 */
public class PluginManagerHelper {

    public static SecretProviderPluginManager newPluginManagerWithMockPlugin() {
        SecretProviderPluginManager pluginManager = new DefaultSecretProviderPluginManager(new DefaultSecretProviderClassLoaderFactory());
        pluginManager.register(
            new DefaultSecretProviderPlugin<>(
                new MockSecretProviderPlugin(true),
                MockSecretProviderFactory.class,
                MockSecretProviderConfiguration.class
            )
        );
        return pluginManager;
    }

    public static class MockSecretProviderPlugin
        implements SecretProviderPlugin<MockSecretProviderFactory, MockSecretProviderConfiguration> {

        private final boolean deployed;

        public MockSecretProviderPlugin(boolean deployed) {
            this.deployed = deployed;
        }

        @Override
        public String id() {
            return "mock";
        }

        @Override
        public String clazz() {
            return MockSecretProviderFactory.class.getName();
        }

        @Override
        public Class<MockSecretProviderFactory> secretProviderFactory() {
            return MockSecretProviderFactory.class;
        }

        @Override
        public Path path() {
            return Path.of("src/test/resources");
        }

        @Override
        public PluginManifest manifest() {
            return new PluginManifest() {
                @Override
                public String id() {
                    return "mock";
                }

                @Override
                public String name() {
                    return "Mock Secret Provider";
                }

                @Override
                public String description() {
                    return "Mock Secret Provider";
                }

                @Override
                public String category() {
                    return "secret providers";
                }

                @Override
                public String version() {
                    return "0.0.0";
                }

                @Override
                public String plugin() {
                    return MockSecretProviderFactory.class.getName();
                }

                @Override
                public String type() {
                    return SecretProvider.PLUGIN_TYPE;
                }

                @Override
                public String feature() {
                    return null;
                }
            };
        }

        @Override
        public URL[] dependencies() {
            return new URL[0];
        }

        @Override
        public boolean deployed() {
            return this.deployed;
        }

        @Override
        public Class<MockSecretProviderConfiguration> configuration() {
            return MockSecretProviderConfiguration.class;
        }
    }
}
