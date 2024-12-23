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

import io.gravitee.am.common.plugin.AmPlugin;
import io.gravitee.am.common.plugin.AmPluginProvider;
import io.gravitee.am.common.plugin.ValidationResult;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.common.service.AbstractService;
import io.gravitee.common.service.Service;
import io.gravitee.plugin.core.api.AbstractConfigurablePluginManager;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class ProviderPluginManager<INSTANCE extends AmPlugin<?, PROVIDER>, PROVIDER extends AmPluginProvider, PROVIDER_CONFIG extends ProviderConfiguration>
        extends AbstractConfigurablePluginManager<INSTANCE> {

    private final static Logger logger = LoggerFactory.getLogger(ProviderPluginManager.class);

    protected final PluginContextFactory pluginContextFactory;

    protected ProviderPluginManager(PluginContextFactory pluginContextFactory) {
        this.pluginContextFactory = pluginContextFactory;
    }

    public abstract PROVIDER create(PROVIDER_CONFIG config);

    public ValidationResult validate(PROVIDER_CONFIG config) {
        return ValidationResult.SUCCEEDED;
    }

    public Plugin findById(String pluginId) {
        return get(pluginId);
    }

    protected <T extends PROVIDER> T createProvider(INSTANCE plugin, BeanFactoryPostProcessor postProcessors) {
        return this.createProvider(plugin, List.of(postProcessors));
    }

    protected <T extends PROVIDER> T createProvider(INSTANCE plugin, List<? extends BeanFactoryPostProcessor> postProcessors) {
        if (plugin.provider() == null) {
            return null;
        }

        return createProvider(new AmPluginContextConfigurer<>(plugin.getDelegate(), (Class<T>) plugin.provider(), postProcessors));
    }

    protected ValidationResult validateProvider(INSTANCE plugin, List<? extends BeanFactoryPostProcessor> postProcessors) {
        try (var provider = createProvider(plugin, postProcessors)) {
            return provider.validate();
        } catch (Exception e) {
            logger.error("Plugin configuration error", e);
            return ValidationResult.invalid("The configuration details entered are incorrect. Please check those and try again.");
        }
    }

    private <T extends PROVIDER> T createProvider(AmPluginContextConfigurer<T> amPluginContextConfigurer) {
        try {
            var pluginApplicationContext = pluginContextFactory.create(amPluginContextConfigurer);

            final AutowireCapableBeanFactory autowireCapableBeanFactory = pluginApplicationContext.getAutowireCapableBeanFactory();
            T provider = autowireCapableBeanFactory.createBean(amPluginContextConfigurer.getProviderClass());

            if (provider instanceof AbstractService) {
                ((AbstractService<?>) provider).setApplicationContext(pluginApplicationContext);
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

    protected <T> T createInstance(Class<T> clazz) throws Exception {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error("Unable to instantiate class: {}", clazz.getName(), ex);
            throw ex;
        }
    }

    public boolean isPluginDeployed(String pluginTypeId) {
        return this.findAll().stream().anyMatch(p -> p.getDelegate().id().equals(pluginTypeId));
    }

    protected INSTANCE getOrThrow(PROVIDER_CONFIG providerConfig) {
        logger.debug("Looking for a provider for [{}]", providerConfig.getType());

        return ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No plugin is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No plugin is registered for type " + providerConfig.getType());
        });
    }

}
