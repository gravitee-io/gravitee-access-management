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
package io.gravitee.am.plugins.idp.core;

import io.gravitee.am.certificate.api.CertificateManager;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.common.service.Service;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.gravitee.plugin.core.internal.AnnotationBasedPluginContextConfigurer;
import io.vertx.reactivex.core.Vertx;
import java.util.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class IdentityProviderPluginManager extends
        ProviderPluginManager<IdentityProvider, AuthenticationProvider, AuthenticationProviderConfiguration>
        implements AmPluginManager<IdentityProvider> {

    protected IdentityProviderPluginManager(PluginContextFactory pluginContextFactory) {
        super(pluginContextFactory);
    }

    public Map<IdentityProvider, Plugin> getAllEntries() {
        return this.plugins;
    }

    public abstract boolean hasUserProvider(String pluginType);

    public abstract UserProvider create(String type, String configuration);

    protected UserProvider createUserProvider(
            Plugin plugin,
            Class<? extends UserProvider> providerClass,
            List<BeanFactoryPostProcessor> beanFactoryPostProcessors) throws Exception {
        if (providerClass == null) {
            return null;
        }

        UserProvider provider = createInstance(providerClass);
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
    }

    protected static class IdentityProviderConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<IdentityProviderConfiguration> {
        public IdentityProviderConfigurationBeanFactoryPostProcessor(IdentityProviderConfiguration configuration) {
            super("configuration", configuration);
        }
    }

    protected static class CertificateManagerBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<CertificateManager> {
        public CertificateManagerBeanFactoryPostProcessor(CertificateManager certificateManager) {
            super("certificateManager", certificateManager);
        }
    }

    protected static class IdentityProviderMapperBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<IdentityProviderMapper> {
        public IdentityProviderMapperBeanFactoryPostProcessor(IdentityProviderMapper mapper) {
            super("mapper", mapper);
        }
    }

    protected static class IdentityProviderRoleMapperBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<IdentityProviderRoleMapper> {
        public IdentityProviderRoleMapperBeanFactoryPostProcessor(IdentityProviderRoleMapper roleMapper) {
            super("roleMapper", roleMapper);
        }
    }

    protected static class PropertiesBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<Properties> {
        public PropertiesBeanFactoryPostProcessor(Properties properties) {
            super("graviteeProperties", properties);
        }
    }

    protected static class VertxBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<Vertx> {
        public VertxBeanFactoryPostProcessor(Vertx vertx) {
            super("vertx", vertx);
        }
    }
}
