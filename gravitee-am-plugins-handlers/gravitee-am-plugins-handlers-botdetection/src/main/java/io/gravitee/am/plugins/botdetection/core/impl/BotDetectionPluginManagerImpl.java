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
package io.gravitee.am.plugins.botdetection.core.impl;

import io.gravitee.am.botdetection.api.BotDetection;
import io.gravitee.am.botdetection.api.BotDetectionConfiguration;
import io.gravitee.am.botdetection.api.BotDetectionProvider;
import io.gravitee.am.plugins.botdetection.core.BotDetectionConfigurationFactory;
import io.gravitee.am.plugins.botdetection.core.BotDetectionDefinition;
import io.gravitee.am.plugins.botdetection.core.BotDetectionPluginManager;
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

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BotDetectionPluginManagerImpl implements BotDetectionPluginManager {

    private final static Logger logger = LoggerFactory.getLogger(BotDetectionPluginManagerImpl.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";

    private final Map<String, BotDetection> botDetections = new HashMap<>();
    private final Map<BotDetection, Plugin> botDetectionPlugins = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private BotDetectionConfigurationFactory botDetectionConfigurationFactory;

    @Override
    public void register(BotDetectionDefinition definition) {
        botDetections.putIfAbsent(definition.getPlugin().id(), definition.getBotDetection());
        botDetectionPlugins.putIfAbsent(definition.getBotDetection(), definition.getPlugin());
    }

    @Override
    public Collection<Plugin> getAll() {
        return this.botDetectionPlugins.values();
    }

    @Override
    public Plugin findById(String pluginId) {
        BotDetection detection = botDetections.get(pluginId);
        return detection != null ? botDetectionPlugins.get(detection) : null;
    }

    @Override
    public BotDetectionProvider create(String type, String configuration) {
        logger.debug("Looking for a bot detection for [{}]", type);
        BotDetection botDetection = botDetections.get(type);

        if (botDetection != null) {
            Class<? extends BotDetectionConfiguration> configurationClass = botDetection.configuration();
            BotDetectionConfiguration botDetectionConfiguration = botDetectionConfigurationFactory.create(configurationClass, configuration);

            return create0(
                    botDetectionPlugins.get(botDetection),
                    botDetection.botDetectionProvider(),
                    botDetectionConfiguration);
        } else {
            logger.error("No bot detection is registered for type {}", type);
            throw new IllegalStateException("No bot detection is registered for type " + type);
        }
    }

    @Override
    public String getSchema(String pluginId) throws IOException {
        BotDetection detection = botDetections.get(pluginId);
        Path policyWorkspace = botDetectionPlugins.get(detection).path();

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

    private <T> T create0(Plugin plugin, Class<T> providerClass, BotDetectionConfiguration botDetectionConfiguration) {
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
                            new BotDetectionConfigurationBeanFactoryPostProcessor(botDetectionConfiguration));

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
            logger.error("An unexpected error occurs while loading bot detection", ex);
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
