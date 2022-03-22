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

package io.gravitee.am.plugins.handlers.api.core;

import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.common.service.Service;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.gravitee.plugin.core.internal.AnnotationBasedPluginContextConfigurer;
import io.gravitee.plugin.core.internal.PluginManifestProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import static java.util.Objects.nonNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class ProviderPluginManager<INSTANCE, PROVIDER, CONFIG extends ProviderConfiguration> {

    private final static Logger logger = LoggerFactory.getLogger(ProviderPluginManager.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";

    protected final Map<String, INSTANCE> instances = new HashMap<>();
    protected final Map<INSTANCE, Plugin> plugins = new HashMap<>();

    protected final PluginContextFactory pluginContextFactory;

    @Autowired
    protected ProviderPluginManager(PluginContextFactory pluginContextFactory) {
        this.pluginContextFactory = pluginContextFactory;
    }

    public abstract PROVIDER create(CONFIG config);

    public void register(INSTANCE instance, Plugin plugin) {
        instances.putIfAbsent(plugin.id(), instance);
        plugins.putIfAbsent(instance, plugin);
    }

    public Plugin findById(String pluginId) {
        INSTANCE instance = instances.get(pluginId);
        return instance != null ? plugins.get(instance) : null;
    }

    public Collection<Plugin> getAll() {
        return this.plugins.values();
    }

    protected <T extends PROVIDER> T createProvider(
            Plugin plugin,
            Class<T> providerClass,
            List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {
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
                    beanFactoryPostProcessors.forEach(configurableApplicationContext::addBeanFactoryPostProcessor);
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
            logger.error("An unexpected error occurs while loading", ex);
            return null;
        }
    }

    public String getSchema(String pluginId) throws IOException {
        INSTANCE instance = instances.get(pluginId);
        if (plugins.containsKey(instance)) {
            Path policyWorkspace = plugins.get(instance).path();

            File[] schemas = policyWorkspace.toFile().listFiles(
                    pathname -> pathname.isDirectory() && pathname.getName().equals(SCHEMAS_DIRECTORY));

            if (nonNull(schemas) && schemas.length == 1) {
                File schemaDir = schemas[0];

                final File[] listFiles = schemaDir.listFiles();
                if (nonNull(listFiles) && listFiles.length > 0) {
                    return new String(Files.readAllBytes(listFiles[0].toPath()));
                }
            }
        }
        return null;
    }

    protected <T> T createInstance(Class<T> clazz) throws Exception {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error("Unable to instantiate class: {}", clazz.getName(), ex);
            throw ex;
        }
    }

    public String getIcon(String resourceId) throws IOException {
        INSTANCE resource = instances.get(resourceId);

        Plugin plugin = plugins.get(resource);
        Map<String, String> properties = plugin.manifest().properties();
        if (properties != null) {
            String icon = properties.get(PluginManifestProperties.MANIFEST_ICON_PROPERTY);
            if (icon != null) {
                Path iconFile = Paths.get(plugin.path().toString(), icon);
                return "data:" + getMimeType(iconFile) + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(iconFile));
            }
        }

        return null;
    }

    private String getMimeType(final Path file) {
        if (file == null || file.getFileName() == null) {
            return null;
        }

        final String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".jpeg") || fileName.endsWith(".jpg")) {
            return "image/jpeg";
        } else {
            return "application/octet-stream";
        }
    }
}
