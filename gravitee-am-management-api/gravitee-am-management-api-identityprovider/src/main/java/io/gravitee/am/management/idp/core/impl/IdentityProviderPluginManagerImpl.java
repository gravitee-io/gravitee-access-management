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
package io.gravitee.am.management.idp.core.impl;

import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProvider;
import io.gravitee.am.management.idp.core.*;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.gravitee.plugin.core.internal.AnnotationBasedPluginContextConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderPluginManagerImpl implements IdentityProviderPluginManager {

    private final Logger logger = LoggerFactory.getLogger(IdentityProviderPluginManagerImpl.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";

    private final Map<String, IdentityProvider> identityProviders = new HashMap<>();
    private final Map<IdentityProvider, Plugin> identityProviderPlugins = new HashMap<>();
    private final Map<IdentityProvider, Plugin> oauth2IdentityProviderPlugins = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private IdentityProviderConfigurationFactory identityProviderConfigurationFactory;

    @Autowired
    private IdentityProviderMapperFactory identityProviderMapperFactory;

    @Autowired
    private IdentityProviderRoleMapperFactory identityProviderRoleMapperFactory;

    @Autowired
    @Qualifier("graviteeProperties")
    private Properties properties;

    @Override
    public void register(IdentityProviderDefinition identityProviderPluginDefinition, boolean oauth2Provider) {
        identityProviders.putIfAbsent(identityProviderPluginDefinition.getPlugin().id(),
                identityProviderPluginDefinition.getIdentityProvider());

        identityProviderPlugins.putIfAbsent(identityProviderPluginDefinition.getIdentityProvider(),
                identityProviderPluginDefinition.getPlugin());

        if (oauth2Provider) {
            oauth2IdentityProviderPlugins.putIfAbsent(identityProviderPluginDefinition.getIdentityProvider(),
                    identityProviderPluginDefinition.getPlugin());
        }
    }

    @Override
    public Collection<Plugin> getAll() {
        Set<String> oauth2ProviderIds = getOAuth2Providers().stream().map(plugin -> plugin.id()).collect(Collectors.toSet());
        return identityProviderPlugins.values().stream().filter(plugin -> !oauth2ProviderIds.contains(plugin.id())).collect(Collectors.toSet());
    }

    @Override
    public Collection<Plugin> getOAuth2Providers() {
        return oauth2IdentityProviderPlugins.values();
    }

    @Override
    public Plugin findById(String identityProviderId) {
        IdentityProvider identityProvider = identityProviders.get(identityProviderId);
        if (identityProvider != null) {
            return (identityProvider instanceof OAuth2IdentityProvider) ? oauth2IdentityProviderPlugins.get(identityProvider) : identityProviderPlugins.get(identityProvider);
        }
        return null;
    }

    @Override
    public AuthenticationProvider create(String type, String configuration, Map<String, String> mappers, Map<String, String[]> roleMapper) {
        logger.debug("Looking for an authentication provider for [{}]", type);
        IdentityProvider identityProvider = identityProviders.get(type);

        if (identityProvider != null) {
            Class<? extends IdentityProviderConfiguration> configurationClass = identityProvider.configuration();
            IdentityProviderConfiguration identityProviderConfiguration = identityProviderConfigurationFactory.create(configurationClass, configuration);

            Class<? extends IdentityProviderMapper> mapperClass = identityProvider.mapper();
            IdentityProviderMapper identityProviderMapper = identityProviderMapperFactory.create(mapperClass, mappers);

            Class<? extends IdentityProviderRoleMapper> roleMapperClass = identityProvider.roleMapper();
            IdentityProviderRoleMapper identityProviderRoleMapper = identityProviderRoleMapperFactory.create(roleMapperClass, roleMapper);

            return create0(
                    identityProviderPlugins.get(identityProvider),
                    identityProvider.authenticationProvider(),
                    identityProviderConfiguration, identityProviderMapper, identityProviderRoleMapper);
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

    private <T> T create0(Plugin plugin, Class<T> identityClass, IdentityProviderConfiguration identityProviderConfiguration,
                          IdentityProviderMapper identityProviderMapper, IdentityProviderRoleMapper identityProviderRoleMapper) {
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

                    // Add gravitee properties
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new PropertiesBeanFactoryPostProcessor(properties));

                    // Add identity provider configuration bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new IdentityProviderConfigurationBeanFactoryPostProcessor(identityProviderConfiguration));

                    // Add identity provider mapper bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new IdentityProviderMapperBeanFactoryPostProcessor(identityProviderMapper));

                    // Add identity provider role mapper bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new IdentityProviderRoleMapperBeanFactoryPostProcessor(identityProviderRoleMapper));

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
