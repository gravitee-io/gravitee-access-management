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
package io.gravitee.am.plugins.deviceidentifier.core.impl;

import io.gravitee.am.plugins.deviceidentifier.core.DeviceIdentifierConfigurationFactory;
import io.gravitee.am.plugins.deviceidentifier.core.DeviceIdentifierDefinition;
import io.gravitee.am.plugins.deviceidentifier.core.DeviceIdentifierPluginManager;
import io.gravitee.am.deviceidentifier.api.DeviceIdentifier;
import io.gravitee.am.deviceidentifier.api.DeviceIdentifierProvider;
import io.gravitee.am.deviceidentifier.api.DeviceIdentifierConfiguration;
import io.gravitee.common.service.Service;
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

import static java.util.Objects.nonNull;

/**
 * @author Rémi Sultan  (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierPluginManagerImpl implements DeviceIdentifierPluginManager {

    private final static Logger logger = LoggerFactory.getLogger(DeviceIdentifierPluginManagerImpl.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";

    private final Map<String, DeviceIdentifier> deviceIdentifierMap = new HashMap<>();
    private final Map<DeviceIdentifier, Plugin> deviceIdentifierPluginMap = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private DeviceIdentifierConfigurationFactory deviceIdentifierConfigurationFactory;


    @Override
    public void register(DeviceIdentifierDefinition definition) {
        deviceIdentifierMap.putIfAbsent(definition.getPlugin().id(), definition.getDeviceIdentifier());
        deviceIdentifierPluginMap.putIfAbsent(definition.getDeviceIdentifier(), definition.getPlugin());
    }

    @Override
    public Collection<Plugin> getAll() {
        return this.deviceIdentifierPluginMap.values();
    }

    @Override
    public Plugin findById(String pluginId) {
        var plugin = deviceIdentifierMap.get(pluginId);
        return plugin != null ? deviceIdentifierPluginMap.get(plugin) : null;
    }

    @Override
    public DeviceIdentifierProvider create(String type, String configuration) {
        logger.debug("Looking for a device identifier for [{}]", type);
        var deviceIdentifier = deviceIdentifierMap.get(type);

        if (deviceIdentifier != null) {
            Class<? extends DeviceIdentifierConfiguration> configurationClass = deviceIdentifier.configuration();
            var deviceIdentifierConfiguration = deviceIdentifierConfigurationFactory.create(configurationClass, configuration);

            return create0(
                    deviceIdentifierPluginMap.get(deviceIdentifier),
                    deviceIdentifier.deviceIdentifierProvider(),
                    deviceIdentifierConfiguration);
        } else {
            logger.error("No device identifier is registered for type {}", type);
            throw new IllegalStateException("No device identifier is registered for type " + type);
        }
    }

    @Override
    public String getSchema(String pluginId) throws IOException {
        var detection = deviceIdentifierMap.get(pluginId);
        Path policyWorkspace = deviceIdentifierPluginMap.get(detection).path();

        File[] schemas = policyWorkspace.toFile().listFiles(pathname -> pathname.isDirectory() && pathname.getName().equals(SCHEMAS_DIRECTORY));

        if (nonNull(schemas) && schemas.length == 1) {
            File schemaDir = schemas[0];

            final File[] fileList = schemaDir.listFiles();
            if (nonNull(fileList) && fileList.length > 0) {
                return new String(Files.readAllBytes(fileList[0].toPath()));
            }
        }

        return null;
    }

    private <T> T create0(Plugin plugin, Class<T> providerClass, DeviceIdentifierConfiguration deviceIdentifierConfiguration) {
        if (providerClass == null) {
            return null;
        }

        try {
            T provider = createInstance(providerClass);
            final Import annImport = providerClass.getAnnotation(Import.class);
            Set<Class<?>> configurations = (annImport != null) ?
                    new HashSet<>(Arrays.asList(annImport.value())) : Collections.emptySet();

            ApplicationContext pluginApplicationContext = pluginContextFactory.create(new AnnotationBasedPluginContextConfigurer(plugin) {
                @Override
                public Set<Class<?>> configurations() {
                    return configurations;
                }

                @Override
                public ConfigurableApplicationContext applicationContext() {
                    ConfigurableApplicationContext configurableApplicationContext = super.applicationContext();

                    // Add authenticator configuration bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new DeviceIdentifierConfigurationBeanFactoryPostProcessor(deviceIdentifierConfiguration));

                    return configurableApplicationContext;
                }
            });

            pluginApplicationContext.getAutowireCapableBeanFactory().autowireBean(provider);

            if (provider instanceof InitializingBean) {
                ((InitializingBean) provider).afterPropertiesSet();
            }

            if (provider instanceof Service) {
                ((Service) provider).start();
            }

            return provider;
        } catch (Exception ex) {
            logger.error("An unexpected error occurs while loading device identifier", ex);
            return null;
        }
    }

    private <T> T createInstance(Class<T> clazz) throws Exception {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error("Unable to instantiate class: {}", ex);
            throw ex;
        }
    }
}
