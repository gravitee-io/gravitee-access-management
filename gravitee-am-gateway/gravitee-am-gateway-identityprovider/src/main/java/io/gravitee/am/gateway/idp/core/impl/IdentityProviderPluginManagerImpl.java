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
package io.gravitee.am.gateway.idp.core.impl;

import io.gravitee.am.gateway.idp.core.IdentityProviderConfigurationFactory;
import io.gravitee.am.gateway.idp.core.IdentityProviderDefinition;
import io.gravitee.am.gateway.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.IdentityProvider;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.gravitee.plugin.core.internal.AnnotationBasedPluginContextConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderPluginManagerImpl implements IdentityProviderPluginManager {

    private final Logger logger = LoggerFactory.getLogger(IdentityProviderPluginManagerImpl.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";

    private final Map<String, IdentityProvider> identityProviders = new HashMap<>();
    private final Map<IdentityProvider, Plugin> identityProviderPlugins = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private IdentityProviderConfigurationFactory identityProviderConfigurationFactory;

    @Override
    public void register(IdentityProviderDefinition identityProviderPluginDefinition) {
        identityProviders.putIfAbsent(identityProviderPluginDefinition.getPlugin().id(),
                identityProviderPluginDefinition.getIdentityProvider());

        identityProviderPlugins.putIfAbsent(identityProviderPluginDefinition.getIdentityProvider(),
                identityProviderPluginDefinition.getPlugin());
    }

    @Override
    public Collection<Plugin> getAll() {
        return identityProviderPlugins.values();
    }

    @Override
    public Plugin findById(String identityProviderId) {
        IdentityProvider identityProvider = identityProviders.get(identityProviderId);
        return (identityProvider != null) ? identityProviderPlugins.get(identityProvider) : null;
    }

    @Override
    public AuthenticationProvider create(String type, String configuration) {
        logger.debug("Looking for an authentication provider for [{}]", type);
        IdentityProvider identityProvider = identityProviders.get(type);

        if (identityProvider != null) {
            Class<? extends IdentityProviderConfiguration> configurationClass = identityProvider.configuration();
            IdentityProviderConfiguration identityProviderConfiguration = identityProviderConfigurationFactory.create(configurationClass, configuration);

            return create0(
                    identityProviderPlugins.get(identityProvider),
                    identityProvider.authenticationProvider(),
                    identityProviderConfiguration);
        } else {
            logger.error("No identity provider is registered for type {}", type);
            throw new IllegalStateException("No identity provider is registered for type " + type);
        }
    }

    @Override
    public String getSchema(String identityProviderId) throws IOException {
        IdentityProvider identityProvider = identityProviders.get(identityProviderId);
        Path policyWorkspace = identityProviderPlugins.get(identityProvider).path();

        File[] schemas = policyWorkspace.toFile().listFiles(
                pathname -> pathname.isDirectory() && pathname.getName().equals(SCHEMAS_DIRECTORY));

        if (schemas.length == 1) {
            File schemaDir = schemas[0];

            if (schemaDir.listFiles().length > 0) {
                return new String(Files.readAllBytes(schemaDir.listFiles()[0].toPath()));
            }
        }

        return null;
    }

    private <T> T create0(Plugin plugin, Class<T> identityClass, IdentityProviderConfiguration identityProviderConfiguration) {
        if (identityClass == null) {
            return null;
        }

        try {
            T identityObj = createInstance(identityClass);
            final Import annImport = identityClass.getAnnotation(Import.class);
            Set<Class<?>> configurations = (annImport != null) ?
                    new HashSet<>(Arrays.asList(annImport.value())) : Collections.emptySet();

            ApplicationContext idpApplicationContext = pluginContextFactory.create(new AnnotationBasedPluginContextConfigurer(plugin) {
                @Override
                public Set<Class<?>> configurations() {
                    return configurations;
                }

                @Override
                public ConfigurableApplicationContext applicationContext() {
                    ConfigurableApplicationContext configurableApplicationContext = super.applicationContext();

                    // Add identity provider configuration bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new IdentityProviderConfigurationBeanFactoryPostProcessor(identityProviderConfiguration));

                    return configurableApplicationContext;
                }
            });

            idpApplicationContext.getAutowireCapableBeanFactory().autowireBean(identityObj);

            if (identityObj instanceof InitializingBean) {
                ((InitializingBean) identityObj).afterPropertiesSet();
            }

            return identityObj;
        } catch (Exception ex) {
            logger.error("An unexpected error occurs while loading identity provider", ex);
            return null;
        }
    }

    private <T> T createInstance(Class<T> clazz) throws Exception {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error("Unable to instantiate class: {}", ex);
            throw ex;
        }
    }
}
