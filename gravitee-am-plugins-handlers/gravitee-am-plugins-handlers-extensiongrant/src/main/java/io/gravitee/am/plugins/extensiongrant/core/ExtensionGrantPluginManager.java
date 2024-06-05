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
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.NoAuthenticationProvider;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantPluginManager
        extends ProviderPluginManager<ExtensionGrant<?, ExtensionGrantProvider>, ExtensionGrantProvider, ExtensionGrantProviderConfiguration>
        implements AmPluginManager<ExtensionGrant<?, ExtensionGrantProvider>> {

    private final Logger logger = LoggerFactory.getLogger(ExtensionGrantPluginManager.class);
    private final ConfigurationFactory<ExtensionGrantConfiguration> configurationFactory;

    public ExtensionGrantPluginManager(PluginContextFactory pluginContextFactory,
                                       ConfigurationFactory<ExtensionGrantConfiguration> extensionGrantConfigurationFactory) {
        super(pluginContextFactory);
        this.configurationFactory = extensionGrantConfigurationFactory;
    }

    @Override
    public ExtensionGrantProvider create(ExtensionGrantProviderConfiguration providerConfig) {
        logger.debug("Looking for an extension grant provider for [{}]", providerConfig.getType());
        var extensionGrant = Optional.ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No extension grant provider is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No extension grant provider is registered for type " + providerConfig.getType());
        });

        var extensionGrantConfiguration = configurationFactory.create(extensionGrant.configuration(), providerConfig.getConfiguration());
        return createProvider(extensionGrant, List.of(
                        new ExtensionGrantConfigurationBeanFactoryPostProcessor(extensionGrantConfiguration),
                        new ExtensionGrantIdentityProviderFactoryPostProcessor(getAuthenticationProvider(providerConfig))
                )
        );
    }

    private static AuthenticationProvider getAuthenticationProvider(ExtensionGrantProviderConfiguration providerConfig) {
        return ofNullable(providerConfig.getAuthenticationProvider()).orElse(new NoAuthenticationProvider());
    }

    private static class ExtensionGrantConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<ExtensionGrantConfiguration> {
        private ExtensionGrantConfigurationBeanFactoryPostProcessor(ExtensionGrantConfiguration configuration) {
            super("configuration", configuration);
        }
    }

    private static class ExtensionGrantIdentityProviderFactoryPostProcessor extends NamedBeanFactoryPostProcessor<AuthenticationProvider> {
        private ExtensionGrantIdentityProviderFactoryPostProcessor(AuthenticationProvider authenticationProvider) {
            super("authenticationProvider", authenticationProvider);
        }
    }
}
