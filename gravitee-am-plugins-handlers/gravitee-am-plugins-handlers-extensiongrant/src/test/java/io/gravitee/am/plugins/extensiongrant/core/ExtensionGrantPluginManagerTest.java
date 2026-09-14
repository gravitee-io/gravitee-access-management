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
package io.gravitee.am.plugins.extensiongrant.core;

import io.gravitee.am.extensiongrant.api.ExtensionGrant;
import io.gravitee.am.extensiongrant.api.ExtensionGrantConfiguration;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.identityprovider.api.trustedissuer.TrustedIssuerResolver;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtensionGrantPluginManagerTest {

    private static final String PLUGIN_TYPE = "cross-app-access-am-extension-grant";

    @Mock
    private PluginContextFactory pluginContextFactory;

    @Mock
    private ConfigurationFactory<ExtensionGrantConfiguration> configurationFactory;

    @Mock
    private ExtensionGrant<ExtensionGrantConfiguration, ExtensionGrantProvider> extensionGrant;

    @Mock
    private ExtensionGrantProvider extensionGrantProvider;

    @Mock
    private TrustedIssuerResolver trustedIssuerResolver;

    private CapturingExtensionGrantPluginManager pluginManager;

    @BeforeEach
    void setUp() {
        pluginManager = new CapturingExtensionGrantPluginManager(pluginContextFactory, configurationFactory);
        when(extensionGrant.configuration()).thenReturn(ExtensionGrantConfiguration.class);
        when(configurationFactory.create(any(), any())).thenReturn(new ExtensionGrantConfiguration() {});
    }

    @Test
    void shouldExposeSuppliedTrustedIssuerResolverToPluginContext() {
        pluginManager.create(new ExtensionGrantProviderConfiguration(grant(), null, trustedIssuerResolver));

        assertSame(trustedIssuerResolver, pluginManager.pluginBeanFactory().getBean("trustedIssuerResolver"));
    }

    @Test
    void shouldExposeResolverFindingNothingWhenNoneSupplied() {
        pluginManager.create(new ExtensionGrantProviderConfiguration(grant(), null, null));

        TrustedIssuerResolver resolver = pluginManager.pluginBeanFactory().getBean("trustedIssuerResolver", TrustedIssuerResolver.class);
        resolver.resolve("https://idp.example.com").test().assertComplete().assertNoValues();
    }

    private static io.gravitee.am.model.ExtensionGrant grant() {
        io.gravitee.am.model.ExtensionGrant grant = new io.gravitee.am.model.ExtensionGrant();
        grant.setType(PLUGIN_TYPE);
        grant.setConfiguration("{}");
        return grant;
    }

    private class CapturingExtensionGrantPluginManager extends ExtensionGrantPluginManager {

        private List<? extends BeanFactoryPostProcessor> postProcessors;

        private CapturingExtensionGrantPluginManager(PluginContextFactory pluginContextFactory,
                                                     ConfigurationFactory<ExtensionGrantConfiguration> configurationFactory) {
            super(pluginContextFactory, configurationFactory);
        }

        @Override
        public ExtensionGrant<?, ExtensionGrantProvider> get(String type) {
            return PLUGIN_TYPE.equals(type) ? extensionGrant : null;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <T extends ExtensionGrantProvider> T createProvider(ExtensionGrant<?, ExtensionGrantProvider> plugin,
                                                                     List<? extends BeanFactoryPostProcessor> postProcessors) {
            this.postProcessors = postProcessors;
            return (T) extensionGrantProvider;
        }

        private DefaultListableBeanFactory pluginBeanFactory() {
            DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
            postProcessors.forEach(postProcessor -> postProcessor.postProcessBeanFactory(beanFactory));
            return beanFactory;
        }
    }
}
