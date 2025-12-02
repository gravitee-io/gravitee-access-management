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
package io.gravitee.am.plugins.authenticator.core;

import io.gravitee.am.authenticator.api.AuthenticatorPlugin;
import io.gravitee.am.authenticator.api.AuthenticatorProvider;
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

import java.util.List;
import java.util.Optional;

public class AuthenticatorPluginManager extends
        ProviderPluginManager<AuthenticatorPlugin<?, AuthenticatorProvider>, AuthenticatorProvider, AuthenticatorProviderConfiguration>
        implements AmPluginManager<AuthenticatorPlugin<?, AuthenticatorProvider>> {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticatorPluginManager.class);
    private final PluginClassLoaderFactory<Plugin> pluginClassLoaderFactory;

    public AuthenticatorPluginManager(
            PluginContextFactory pluginContextFactory,
            PluginClassLoaderFactory<Plugin> pluginClassLoaderFactory) {
        super(pluginContextFactory);
        this.pluginClassLoaderFactory = pluginClassLoaderFactory;
    }

    public List<AuthenticatorProvider> createAll(ApplicationContext applicationContext){
        return findAll().stream()
                .flatMap(auth -> create(auth, applicationContext).stream())
                .toList();
    }


    @Override
    public AuthenticatorProvider create(AuthenticatorProviderConfiguration providerConfig) {
        logger.debug("Looking for an authenticator provider for [{}]", providerConfig.getType());
        var authenticator = get(providerConfig.getType());
        if (authenticator != null) {
            try {
                return create(authenticator, providerConfig.getApplicationContext()).orElse(null);
            } catch (Exception ex) {
                logger.error("An unexpected error occurs while loading authenticator", ex);
            }
        } else {
            logger.info("No authenticator provider is registered for type {}", providerConfig.getType());
        }
        return null;
    }


    private Optional<AuthenticatorProvider> create(AuthenticatorPlugin<?,?> authenticator, ApplicationContext parentContext) {
            try {
                var authenticatorProvider = createInstance(authenticator.provider());

                final Environment environment = parentContext.getEnvironment();

                AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                context.setParent(parentContext);
                context.setClassLoader(pluginClassLoaderFactory.getOrCreateClassLoader(authenticator));
                context.setEnvironment((ConfigurableEnvironment) environment);

                PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
                configurer.setIgnoreUnresolvablePlaceholders(true);
                configurer.setEnvironment(environment);
                context.addBeanFactoryPostProcessor(configurer);

                context.register(authenticator.configuration());
                context.registerBeanDefinition(authenticator.clazz(), BeanDefinitionBuilder.rootBeanDefinition(authenticator.clazz()).getBeanDefinition());
                context.refresh();

                context.getAutowireCapableBeanFactory().autowireBean(authenticatorProvider);

                if (authenticatorProvider instanceof InitializingBean bean) {
                    bean.afterPropertiesSet();
                }
                return Optional.of(authenticatorProvider);
            } catch (Exception ex) {
                logger.error("An unexpected error occurs while loading authenticator", ex);
            }
            return Optional.empty();
    }

}
