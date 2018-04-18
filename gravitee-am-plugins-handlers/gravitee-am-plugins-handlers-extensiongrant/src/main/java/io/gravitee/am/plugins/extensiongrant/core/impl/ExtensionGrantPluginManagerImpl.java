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
package io.gravitee.am.plugins.extensiongrant.core.impl;

import io.gravitee.am.extensiongrant.api.ExtensionGrant;
import io.gravitee.am.extensiongrant.api.ExtensionGrantConfiguration;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.NoAuthenticationProvider;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantConfigurationFactory;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantDefinition;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantPluginManager;
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
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantPluginManagerImpl implements ExtensionGrantPluginManager {

    private final Logger logger = LoggerFactory.getLogger(ExtensionGrantPluginManagerImpl.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";

    private final Map<String, ExtensionGrant> extensionGrants = new HashMap<>();
    private final Map<ExtensionGrant, Plugin> extensionGrantPlugins = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private ExtensionGrantConfigurationFactory extensionGrantConfigurationFactory;

    @Override
    public void register(ExtensionGrantDefinition extensionGrantDefinition) {
        extensionGrants.putIfAbsent(extensionGrantDefinition.getPlugin().id(),
                extensionGrantDefinition.getExtensionGrant());

        extensionGrantPlugins.putIfAbsent(extensionGrantDefinition.getExtensionGrant(),
                extensionGrantDefinition.getPlugin());
    }

    @Override
    public Collection<Plugin> getAll() {
        return extensionGrantPlugins.values();
    }

    @Override
    public Plugin findById(String identityProviderId) {
        ExtensionGrant extensionGrant = extensionGrants.get(identityProviderId);
        return (extensionGrant != null) ? extensionGrantPlugins.get(extensionGrant) : null;
    }

    @Override
    public ExtensionGrantProvider create(String type, String configuration, AuthenticationProvider authenticationProvider) {
        logger.debug("Looking for an extension grant provider for [{}]", type);
        ExtensionGrant extensionGrant = extensionGrants.get(type);

        if (extensionGrant != null) {
            Class<? extends ExtensionGrantConfiguration> configurationClass = extensionGrant.configuration();
            ExtensionGrantConfiguration extensionGrantConfiguration = extensionGrantConfigurationFactory.create(configurationClass, configuration);

            return create0(extensionGrantPlugins.get(extensionGrant),
                    extensionGrant.provider(),
                    extensionGrantConfiguration, authenticationProvider);
        } else {
            logger.error("No extension grant provider is registered for type {}", type);
            throw new IllegalStateException("No extension grant provider is registered for type " + type);
        }
    }

    @Override
    public String getSchema(String tokenGranterId) throws IOException {
        ExtensionGrant extensionGrant = extensionGrants.get(tokenGranterId);
        Path policyWorkspace = extensionGrantPlugins.get(extensionGrant).path();

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

    private <T> T create0(Plugin plugin, Class<T> extensionGrantClass, ExtensionGrantConfiguration extensionGrantConfiguration, AuthenticationProvider authenticationProvider) {
        if (extensionGrantClass == null) {
            return null;
        }

        try {
            T extensionGrantObj = createInstance(extensionGrantClass);
            final Import annImport = extensionGrantClass.getAnnotation(Import.class);
            Set<Class<?>> configurations = (annImport != null) ?
                    new HashSet<>(Arrays.asList(annImport.value())) : Collections.emptySet();

            ApplicationContext extensionGrantApplicationContext = pluginContextFactory.create(new AnnotationBasedPluginContextConfigurer(plugin) {
                @Override
                public Set<Class<?>> configurations() {
                    return configurations;
                }

                @Override
                public ConfigurableApplicationContext applicationContext() {
                    ConfigurableApplicationContext configurableApplicationContext = super.applicationContext();

                    // Add extension grant configuration bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new ExtensionGrantConfigurationBeanFactoryPostProcessor(extensionGrantConfiguration));

                    // Add extension grant identity provider bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new ExtensionGrantIdentityProviderFactoryPostProcessor(authenticationProvider != null ? authenticationProvider : new NoAuthenticationProvider()));

                    return configurableApplicationContext;
                }
            });

            extensionGrantApplicationContext.getAutowireCapableBeanFactory().autowireBean(extensionGrantObj);

            if (extensionGrantObj instanceof InitializingBean) {
                ((InitializingBean) extensionGrantObj).afterPropertiesSet();
            }

            return extensionGrantObj;
        } catch (Exception ex) {
            logger.error("An unexpected error occurs while loading extension grant", ex);
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
