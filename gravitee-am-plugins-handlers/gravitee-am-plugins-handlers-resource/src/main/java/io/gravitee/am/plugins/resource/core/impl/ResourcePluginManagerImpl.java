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
package io.gravitee.am.plugins.resource.core.impl;

import io.gravitee.am.plugins.resource.core.ResourceConfigurationFactory;
import io.gravitee.am.plugins.resource.core.ResourceDefinition;
import io.gravitee.am.plugins.resource.core.ResourcePluginManager;
import io.gravitee.am.resource.api.Resource;
import io.gravitee.am.resource.api.ResourceConfiguration;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.gravitee.plugin.core.internal.AnnotationBasedPluginContextConfigurer;
import io.gravitee.plugin.core.internal.PluginManifestProperties;
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
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourcePluginManagerImpl implements ResourcePluginManager {

    private final Logger logger = LoggerFactory.getLogger(ResourcePluginManagerImpl.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";

    private final Map<String, Resource> resources = new HashMap<>();
    private final Map<Resource, Plugin> resourcePlugins = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private ResourceConfigurationFactory resourceConfigurationFactory;

    @Override
    public void register(ResourceDefinition resourceDefinition) {
        resources.putIfAbsent(resourceDefinition.getPlugin().id(),
                resourceDefinition.getResource());

        resourcePlugins.putIfAbsent(resourceDefinition.getResource(),
                resourceDefinition.getPlugin());
    }

    @Override
    public Collection<Plugin> getAll() {
        return resourcePlugins.values();
    }

    @Override
    public Plugin findById(String resId) {
        Resource resource = resources.get(resId);
        return resource != null ? resourcePlugins.get(resource) : null;
    }

    @Override
    public ResourceProvider create(String type, String configuration) {
        logger.debug("Looking for a resource for [{}]", type);
        Resource resource = resources.get(type);

        if (resource != null) {
            Class<? extends ResourceConfiguration> configurationClass = resource.configuration();
            ResourceConfiguration resourceConfiguration = resourceConfigurationFactory.create(configurationClass, configuration);

            return create0(
                    resourcePlugins.get(resource),
                    resource.resourceProvider(),
                    resourceConfiguration);
        } else {
            logger.error("No resource is registered for type {}", type);
            throw new IllegalStateException("No resource is registered for type " + type);
        }
    }

    @Override
    public String getSchema(String resId) throws IOException {
        Resource resource = resources.get(resId);
        if (resourcePlugins.containsKey(resource)) {
            Path policyWorkspace = resourcePlugins.get(resource).path();

            File[] schemas = policyWorkspace.toFile().listFiles(
                    pathname -> pathname.isDirectory() && pathname.getName().equals(SCHEMAS_DIRECTORY));

            if (schemas.length == 1) {
                File schemaDir = schemas[0];

                if (schemaDir.listFiles().length > 0) {
                    return new String(Files.readAllBytes(schemaDir.listFiles()[0].toPath()));
                }
            }
        }
        return null;
    }

    @Override
    public String getIcon(String resourceId) throws IOException {
        Resource resource = resources.get(resourceId);

        Plugin plugin = resourcePlugins.get(resource);
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

    private <T> T create0(Plugin plugin, Class<T> identityClass, ResourceConfiguration resourceConfiguration) {
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

                    // Add resource configuration bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new ResourceConfigurationBeanFactoryPostProcessor(resourceConfiguration));

                    return configurableApplicationContext;
                }
            });

            idpApplicationContext.getAutowireCapableBeanFactory().autowireBean(identityObj);

            if (identityObj instanceof InitializingBean) {
                ((InitializingBean) identityObj).afterPropertiesSet();
            }

            return identityObj;
        } catch (Exception ex) {
            logger.error("An unexpected error occurs while loading resource", ex);
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
