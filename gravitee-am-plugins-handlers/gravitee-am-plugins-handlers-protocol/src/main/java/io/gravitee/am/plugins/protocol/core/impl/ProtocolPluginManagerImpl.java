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
package io.gravitee.am.plugins.protocol.core.impl;

import io.gravitee.am.gateway.handler.api.Protocol;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.plugins.protocol.core.ProtocolDefinition;
import io.gravitee.am.plugins.protocol.core.ProtocolPluginManager;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ProtocolPluginManagerImpl implements ProtocolPluginManager {

    private static final Logger logger = LoggerFactory.getLogger(ProtocolPluginManagerImpl.class);
    private final Map<String, Protocol> protocols = new HashMap<>();
    private final Map<Protocol, Plugin> protocolPlugins = new HashMap<>();

    @Autowired
    private PluginClassLoaderFactory pluginClassLoaderFactory;

    @Override
    public void register(ProtocolDefinition protocolDefinition) {
        protocols.putIfAbsent(protocolDefinition.getPlugin().id(),
                protocolDefinition.getProtocol());

        protocolPlugins.putIfAbsent(protocolDefinition.getProtocol(),
                protocolDefinition.getPlugin());

    }

    @Override
    public ProtocolProvider create(String type, ApplicationContext parentContext) {
        logger.debug("Looking for an protocol provider for [{}]", type);
        Protocol protocol = protocols.get(type);
        if (protocol != null) {
            try {
                ProtocolProvider protocolProvider = createInstance(protocol.protocolProvider());
                Plugin plugin = protocolPlugins.get(protocol);

                AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                context.setParent(parentContext);
                context.setClassLoader(pluginClassLoaderFactory.getOrCreateClassLoader(plugin));
                context.setEnvironment((ConfigurableEnvironment) parentContext.getEnvironment());

                PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
                configurer.setIgnoreUnresolvablePlaceholders(true);
                configurer.setEnvironment(parentContext.getEnvironment());
                context.addBeanFactoryPostProcessor(configurer);

                context.register(protocol.configuration());
                context.registerBeanDefinition(plugin.clazz(), BeanDefinitionBuilder.rootBeanDefinition(plugin.clazz()).getBeanDefinition());
                context.refresh();

                context.getAutowireCapableBeanFactory().autowireBean(protocolProvider);

                if (protocolProvider instanceof InitializingBean) {
                    ((InitializingBean) protocolProvider).afterPropertiesSet();
                }
                return protocolProvider;
            } catch (Exception ex) {
                logger.error("An unexpected error occurs while loading protocol", ex);
                return null;
            }
        } else {
            logger.error("No protocol provider is registered for type {}", type);
            throw new IllegalStateException("No protocol provider is registered for type " + type);
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
