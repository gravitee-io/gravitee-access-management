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
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.IdentityProvider;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.plugins.handlers.api.core.AmPluginContextConfigurer;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;

import java.util.Optional;
import java.util.Properties;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class IdentityProviderPluginManager extends
        ProviderPluginManager<IdentityProvider<?, AuthenticationProvider>, AuthenticationProvider, AuthenticationProviderConfiguration>
        implements AmPluginManager<IdentityProvider<?, AuthenticationProvider>> {

    protected IdentityProviderPluginManager(PluginContextFactory pluginContextFactory) {
        super(pluginContextFactory);
    }

    public abstract boolean hasUserProvider(String pluginType);

    public abstract Single<Optional<UserProvider>> create(String type, String configuration, io.gravitee.am.model.IdentityProvider identityProvider);

    protected Single<UserProvider> createUserProvider(AmPluginContextConfigurer<? extends UserProvider> amPluginContextConfigurer) throws Exception {
        if (amPluginContextConfigurer.getProviderClass() == null) {
            return null;
        }

        UserProvider provider = createInstance(amPluginContextConfigurer.getProviderClass());
        ApplicationContext pluginApplicationContext = pluginContextFactory.create(amPluginContextConfigurer);

        pluginApplicationContext.getAutowireCapableBeanFactory().autowireBean(provider);

        if (provider instanceof InitializingBean) {
            ((InitializingBean) provider).afterPropertiesSet();
        }

        return provider.asyncStart();
    }

    protected static class IdentityProviderEntityBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<io.gravitee.am.model.IdentityProvider> {
        public IdentityProviderEntityBeanFactoryPostProcessor(io.gravitee.am.model.IdentityProvider configuration) {
            super("identityProviderEntity", configuration);
        }
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
