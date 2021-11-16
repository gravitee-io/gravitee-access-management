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
package io.gravitee.am.plugins.authdevice.notifier.core.impl;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifier;
import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierConfiguration;
import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.plugins.authdevice.notifier.core.AuthenticationDeviceNotifierConfigurationFactory;
import io.gravitee.am.plugins.authdevice.notifier.core.AuthenticationDeviceNotifierDefinition;
import io.gravitee.am.plugins.authdevice.notifier.core.AuthenticationDeviceNotifierPluginManager;
import io.gravitee.common.service.Service;
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
public class AuthenticationDeviceNotifierPluginManagerImpl implements AuthenticationDeviceNotifierPluginManager {

    private final static Logger logger = LoggerFactory.getLogger(AuthenticationDeviceNotifierPluginManagerImpl.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";

    private final Map<String, AuthenticationDeviceNotifier> authDeviceNotifiers = new HashMap<>();
    private final Map<AuthenticationDeviceNotifier, Plugin> authDeviceNotifierPlugins = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private AuthenticationDeviceNotifierConfigurationFactory authDeviceNotifierConfigurationFactory;

    @Override
    public void register(AuthenticationDeviceNotifierDefinition definition) {
        authDeviceNotifiers.putIfAbsent(definition.getPlugin().id(), definition.getAuthDeviceNotifier());
        authDeviceNotifierPlugins.putIfAbsent(definition.getAuthDeviceNotifier(), definition.getPlugin());
    }

    @Override
    public Collection<Plugin> getAll() {
        return this.authDeviceNotifierPlugins.values();
    }

    @Override
    public Plugin findById(String pluginId) {
        AuthenticationDeviceNotifier detection = authDeviceNotifiers.get(pluginId);
        return detection != null ? authDeviceNotifierPlugins.get(detection) : null;
    }

    @Override
    public AuthenticationDeviceNotifierProvider create(String type, String configuration) {
        logger.debug("Looking for an authentication device notifier for [{}]", type);
        AuthenticationDeviceNotifier authDeviceNotifier = authDeviceNotifiers.get(type);

        if (authDeviceNotifier != null) {
            Class<? extends AuthenticationDeviceNotifierConfiguration> configurationClass = authDeviceNotifier.configuration();
            AuthenticationDeviceNotifierConfiguration botDetectionConfiguration = authDeviceNotifierConfigurationFactory.create(configurationClass, configuration);

            return create0(
                    authDeviceNotifierPlugins.get(authDeviceNotifier),
                    authDeviceNotifier.notificationProvider(),
                    botDetectionConfiguration);
        } else {
            logger.error("No authentication device notifier is registered for type {}", type);
            throw new IllegalStateException("No authentication device notifier is registered for type " + type);
        }
    }

    @Override
    public String getSchema(String pluginId) throws IOException {
        AuthenticationDeviceNotifier detection = authDeviceNotifiers.get(pluginId);
        Path policyWorkspace = authDeviceNotifierPlugins.get(detection).path();

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

    @Override
    public String getIcon(String pluginId) throws IOException {
        AuthenticationDeviceNotifier notifier = this.authDeviceNotifiers.get(pluginId);

        Plugin plugin = this.authDeviceNotifierPlugins.get(notifier);
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

    private <T> T create0(Plugin plugin, Class<T> providerClass, AuthenticationDeviceNotifierConfiguration botDetectionConfiguration) {
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
                            new AuthenticationDeviceNotifierConfigurationBeanFactoryPostProcessor(botDetectionConfiguration));

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
            logger.error("An unexpected error occurs while loading authentication device notifier", ex);
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
