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
package io.gravitee.am.plugins.protocol.core;

import io.gravitee.am.gateway.handler.api.Protocol;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ProtocolPluginManager extends
        ProviderPluginManager<Protocol<?, ProtocolProvider>, ProtocolProvider, ProtocolProviderConfiguration>
        implements AmPluginManager<Protocol<?, ProtocolProvider>> {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolPluginManager.class);
    private final PluginClassLoaderFactory<Plugin> pluginClassLoaderFactory;

    public ProtocolPluginManager(
            PluginContextFactory pluginContextFactory,
            PluginClassLoaderFactory<Plugin> pluginClassLoaderFactory
    ) {
        super(pluginContextFactory);
        this.pluginClassLoaderFactory = pluginClassLoaderFactory;
    }

    @Override
    public ProtocolProvider create(ProtocolProviderConfiguration providerConfig) {
        logger.debug("Looking for an protocol provider for [{}]", providerConfig.getType());
        var protocol = get(providerConfig.getType());
        if (protocol != null) {
            try {
                var protocolProvider = createInstance(protocol.provider());

                final ApplicationContext parentContext = providerConfig.getApplicationContext();
                final Environment environment = parentContext.getEnvironment();

                AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                context.setParent(parentContext);
                context.setClassLoader(pluginClassLoaderFactory.getOrCreateClassLoader(protocol));
                context.setEnvironment((ConfigurableEnvironment) environment);

                PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
                configurer.setIgnoreUnresolvablePlaceholders(true);
                configurer.setEnvironment(parentContext.getEnvironment());
                context.addBeanFactoryPostProcessor(configurer);

                context.register(protocol.configuration());
                context.registerBeanDefinition(protocol.clazz(), BeanDefinitionBuilder.rootBeanDefinition(protocol.clazz()).getBeanDefinition());
                context.refresh();

                context.getAutowireCapableBeanFactory().autowireBean(protocolProvider);
                if (protocolProvider instanceof InitializingBean bean) {
                    bean.afterPropertiesSet();
                }
                return protocolProvider;
            } catch (Exception ex) {
                logger.error("An unexpected error occurs while loading protocol", ex);
            }
        } else {
            logger.info("No protocol provider is registered for type {}", providerConfig.getType());
        }
        return null;
    }
}
