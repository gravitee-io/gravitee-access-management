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
import io.gravitee.am.plugins.handlers.api.core.AMPluginManager;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.plugin.core.api.PluginContextFactory;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class ExtensionGrantPluginManager
        extends ProviderPluginManager<ExtensionGrant, ExtensionGrantProvider, ExtensionGrantProviderConfiguration>
        implements AMPluginManager<ExtensionGrant> {

    protected ExtensionGrantPluginManager(PluginContextFactory pluginContextFactory) {
        super(pluginContextFactory);
    }

    protected static class ExtensionGrantConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<ExtensionGrantConfiguration> {
        public ExtensionGrantConfigurationBeanFactoryPostProcessor(ExtensionGrantConfiguration configuration) {
            super("configuration", configuration);
        }
    }

    protected static class ExtensionGrantIdentityProviderFactoryPostProcessor extends NamedBeanFactoryPostProcessor<AuthenticationProvider> {
        public ExtensionGrantIdentityProviderFactoryPostProcessor(AuthenticationProvider authenticationProvider) {
            super("authenticationProvider", authenticationProvider);
        }
    }
}