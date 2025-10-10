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
package io.gravitee.am.plugins.authorizationengine.core;

import io.gravitee.am.authorizationengine.api.AuthorizationEngine;
import io.gravitee.am.authorizationengine.api.AuthorizationEngineConfiguration;
import io.gravitee.am.authorizationengine.api.AuthorizationEngineProvider;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationEnginePluginManagerTest {

    @Mock
    private PluginContextFactory pluginContextFactory;

    @Mock
    private ConfigurationFactory<AuthorizationEngineConfiguration> configurationFactory;

    @Mock
    private AuthorizationEngine<AuthorizationEngineConfiguration, AuthorizationEngineProvider> authorizationEngine;

    @Mock
    private AuthorizationEngineConfiguration authorizationEngineConfiguration;

    @Mock
    private AuthorizationEngineProvider authorizationEngineProvider;

    private TestAuthorizationEnginePluginManager pluginManager;

    @BeforeEach
    void setUp() {
        pluginManager = new TestAuthorizationEnginePluginManager(pluginContextFactory, configurationFactory);
    }

    @Test
    void shouldCreateProviderWithValidConfiguration() {
        // given
        var pluginType = "openfga";
        var configuration = "{\"connectionUri\":\"http://localhost:8080\"}";
        var providerConfig = new ProviderConfiguration(pluginType, configuration);
        pluginManager.setRegisteredAuthorizationEngine(pluginType, authorizationEngine);
        pluginManager.setAuthorizationEngineProvider(authorizationEngineProvider);
        pluginManager.resetCapturedPostProcessor();
        when(authorizationEngine.configuration()).thenReturn(AuthorizationEngineConfiguration.class);
        when(configurationFactory.create(AuthorizationEngineConfiguration.class, configuration))
                .thenReturn(authorizationEngineConfiguration);

        // when
        var provider = pluginManager.create(providerConfig);

        // then
        assertNotNull(provider);
        assertSame(authorizationEngineProvider, provider);
        verify(configurationFactory).create(AuthorizationEngineConfiguration.class, configuration);
        assertSame(authorizationEngine, pluginManager.getLastCreateProviderInstance());
        assertNotNull(pluginManager.getLastBeanFactoryPostProcessor());
    }

    @Test
    void shouldThrowExceptionWhenAuthorizationEngineNotRegistered() {
        // given
        String pluginType = "non-existent-type";
        String configuration = "{}";
        ProviderConfiguration providerConfig = new ProviderConfiguration(pluginType, configuration);

        // when & then
        assertThrows(IllegalStateException.class, () -> {
            pluginManager.create(providerConfig);
        });
    }

    @Test
    void shouldHandleNullProviderConfiguration() {
        // when & then
        assertThrows(NullPointerException.class, () -> {
            pluginManager.create(null);
        });
    }

    @Test
    void shouldHandleInvalidConfigurationJson() {
        // given
        var pluginType = "openfga";
        var invalidConfiguration = "invalid-json";
        var providerConfig = new ProviderConfiguration(pluginType, invalidConfiguration);
        var expectedException = new IllegalArgumentException("invalid");
        pluginManager.setRegisteredAuthorizationEngine(pluginType, authorizationEngine);
        pluginManager.setAuthorizationEngineProvider(authorizationEngineProvider);
        when(authorizationEngine.configuration()).thenReturn(AuthorizationEngineConfiguration.class);
        when(configurationFactory.create(AuthorizationEngineConfiguration.class, invalidConfiguration))
                .thenThrow(expectedException);

        // when + then
        var thrown = assertThrows(IllegalArgumentException.class, () -> pluginManager.create(providerConfig));
        assertSame(expectedException, thrown);
    }

    @Test
    void shouldHandleEmptyConfiguration() {
        // given
        var pluginType = "openfga";
        var emptyConfiguration = "";
        var providerConfig = new ProviderConfiguration(pluginType, emptyConfiguration);
        pluginManager.setRegisteredAuthorizationEngine(pluginType, authorizationEngine);
        pluginManager.setAuthorizationEngineProvider(authorizationEngineProvider);
        when(authorizationEngine.configuration()).thenReturn(AuthorizationEngineConfiguration.class);
        when(configurationFactory.create(AuthorizationEngineConfiguration.class, emptyConfiguration))
                .thenReturn(authorizationEngineConfiguration);

        // when
        var provider = pluginManager.create(providerConfig);

        // then
        assertNotNull(provider);
        assertSame(authorizationEngineProvider, provider);
    }

    @Test
    void shouldValidateProviderConfigurationType() {
        // given
        String pluginType = "openfga";
        ProviderConfiguration providerConfig = new ProviderConfiguration(pluginType, "{}");

        assertNotNull(providerConfig.getType());
        assertEquals(pluginType, providerConfig.getType());
    }
    private static class TestAuthorizationEnginePluginManager extends AuthorizationEnginePluginManager {

        private AuthorizationEngine<AuthorizationEngineConfiguration, AuthorizationEngineProvider> registeredAuthorizationEngine;
        private String registeredType;
        private AuthorizationEngineProvider authorizationEngineProvider;
        private AuthorizationEngine<AuthorizationEngineConfiguration, AuthorizationEngineProvider> lastCreateProviderInstance;
        private BeanFactoryPostProcessor lastBeanFactoryPostProcessor;

        private TestAuthorizationEnginePluginManager(PluginContextFactory pluginContextFactory,
                                                     ConfigurationFactory<AuthorizationEngineConfiguration> configurationFactory) {
            super(pluginContextFactory, configurationFactory);
        }

        void setRegisteredAuthorizationEngine(String type, AuthorizationEngine<AuthorizationEngineConfiguration, AuthorizationEngineProvider> authorizationEngine) {
            this.registeredType = type;
            this.registeredAuthorizationEngine = authorizationEngine;
        }

        void setAuthorizationEngineProvider(AuthorizationEngineProvider authorizationEngineProvider) {
            this.authorizationEngineProvider = authorizationEngineProvider;
        }

        void resetCapturedPostProcessor() {
            this.lastCreateProviderInstance = null;
            this.lastBeanFactoryPostProcessor = null;
        }

        @Override
        public AuthorizationEngine<?, AuthorizationEngineProvider> get(String type) {
            if (registeredType != null && registeredType.equals(type)) {
                return registeredAuthorizationEngine;
            }
            return super.get(type);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <T extends AuthorizationEngineProvider> T createProvider(
                AuthorizationEngine<?, AuthorizationEngineProvider> plugin,
                BeanFactoryPostProcessor postProcessor
        ) {
            this.lastCreateProviderInstance = (AuthorizationEngine<AuthorizationEngineConfiguration, AuthorizationEngineProvider>) plugin;
            this.lastBeanFactoryPostProcessor = postProcessor;
            return (T) authorizationEngineProvider;
        }

        AuthorizationEngine<?, AuthorizationEngineProvider> getLastCreateProviderInstance() {
            return lastCreateProviderInstance;
        }

        BeanFactoryPostProcessor getLastBeanFactoryPostProcessor() {
            return lastBeanFactoryPostProcessor;
        }
    }
}
