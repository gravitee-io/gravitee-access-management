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
package io.gravitee.am.plugins.idp.core.impl;

import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.IdentityProvider;
import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.NoIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.plugins.idp.core.AuthenticationProviderConfiguration;
import io.gravitee.am.plugins.idp.core.IdentityProviderMapperFactory;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.plugins.idp.core.IdentityProviderRoleMapperFactory;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.reactivex.Single;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderPluginManagerImpl extends IdentityProviderPluginManager {

    private final Logger logger = LoggerFactory.getLogger(IdentityProviderPluginManagerImpl.class);

    private final ConfigurationFactory<IdentityProviderConfiguration> identityProviderConfigurationFactory;
    private final IdentityProviderMapperFactory identityProviderMapperFactory;
    private final IdentityProviderRoleMapperFactory identityProviderRoleMapperFactory;
    private final Properties graviteeProperties;
    private final Vertx vertx;

    public IdentityProviderPluginManagerImpl(PluginContextFactory pluginContextFactory,
                                             ConfigurationFactory<IdentityProviderConfiguration> identityProviderConfigurationFactory,
                                             IdentityProviderMapperFactory identityProviderMapperFactory,
                                             IdentityProviderRoleMapperFactory identityProviderRoleMapperFactory,
                                             Properties graviteeProperties,
                                             Vertx vertx
    ) {
        super(pluginContextFactory);
        this.identityProviderConfigurationFactory = identityProviderConfigurationFactory;
        this.identityProviderMapperFactory = identityProviderMapperFactory;
        this.identityProviderRoleMapperFactory = identityProviderRoleMapperFactory;
        this.graviteeProperties = graviteeProperties;
        this.vertx = vertx;
    }

    @Override
    public boolean hasUserProvider(String pluginType) {
        logger.debug("Looking for an user provider for [{}]", pluginType);
        IdentityProvider identityProvider = instances.get(pluginType);
        return identityProvider != null && identityProvider.userProvider() != null;
    }

    @Override
    public AuthenticationProvider create(AuthenticationProviderConfiguration providerConfiguration) {
        logger.debug("Looking for an authentication provider for [{}]", providerConfiguration.getType());
        IdentityProvider identityProvider = instances.get(providerConfiguration.getType());

        if (identityProvider != null) {
            Class<? extends IdentityProviderConfiguration> configurationClass = identityProvider.configuration();
            IdentityProviderConfiguration identityProviderConfiguration = identityProviderConfigurationFactory.create(configurationClass, providerConfiguration.getConfiguration());

            Class<? extends IdentityProviderMapper> mapperClass = identityProvider.mapper();
            IdentityProviderMapper identityProviderMapper = identityProviderMapperFactory.create(mapperClass, providerConfiguration.getMappers());

            Class<? extends IdentityProviderRoleMapper> roleMapperClass = identityProvider.roleMapper();
            IdentityProviderRoleMapper identityProviderRoleMapper = identityProviderRoleMapperFactory.create(roleMapperClass, providerConfiguration.getRoleMapper());

            final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = Stream.of(
                    new IdentityProviderConfigurationBeanFactoryPostProcessor(identityProviderConfiguration),
                    new IdentityProviderMapperBeanFactoryPostProcessor(identityProviderMapper),
                    new IdentityProviderRoleMapperBeanFactoryPostProcessor(identityProviderRoleMapper),
                    new PropertiesBeanFactoryPostProcessor(graviteeProperties),
                    new VertxBeanFactoryPostProcessor(vertx),
                    new IdentityProviderEntityBeanFactoryPostProcessor(providerConfiguration.getIdentityProvider())
            ).collect(toList());

            ofNullable(providerConfiguration.getCertificateManager()).ifPresent(certificateManager ->
                    beanFactoryPostProcessors.add(new CertificateManagerBeanFactoryPostProcessor(providerConfiguration.getCertificateManager()))
            );

            return createProvider(
                    plugins.get(identityProvider),
                    identityProvider.authenticationProvider(),
                    beanFactoryPostProcessors
            );
        } else {
            logger.error("No identity provider is registered for type {}", providerConfiguration.getType());
            throw new IllegalStateException("No identity provider is registered for type " + providerConfiguration.getType());
        }
    }

    @Override
    public Single<Optional<UserProvider>> create(String type, String configuration, io.gravitee.am.model.IdentityProvider identityProviderEntity) {
        logger.debug("Looking for an user provider for [{}]", type);
        var providerConfiguration = new ProviderConfiguration(type, configuration);
        IdentityProvider identityProvider = instances.get(providerConfiguration.getType());

        if (identityProvider != null) {
            Class<? extends IdentityProviderConfiguration> configurationClass = identityProvider.configuration();
            IdentityProviderConfiguration identityProviderConfiguration = identityProviderConfigurationFactory.create(configurationClass, providerConfiguration.getConfiguration());

            if (identityProvider.userProvider() == null || !identityProviderConfiguration.userProvider()) {
                logger.info("No user provider is registered for type {}", providerConfiguration.getType());
                return Single.just(Optional.empty());
            }

            Class<? extends IdentityProviderMapper> mapperClass = identityProvider.mapper();
            IdentityProviderMapper identityProviderMapper = identityProviderMapperFactory.create(mapperClass, identityProviderEntity.getMappers());

            try {
                return createUserProvider(
                        plugins.get(identityProvider),
                        identityProvider.userProvider(),
                        List.of(
                                new IdentityProviderConfigurationBeanFactoryPostProcessor(identityProviderConfiguration),
                                new PropertiesBeanFactoryPostProcessor(graviteeProperties),
                                new VertxBeanFactoryPostProcessor(vertx),
                                new IdentityProviderEntityBeanFactoryPostProcessor(identityProviderEntity),
                                new IdentityProviderMapperBeanFactoryPostProcessor(identityProviderMapper != null ? identityProviderMapper : new NoIdentityProviderMapper())
                        )
                ).map(Optional::of);
            } catch (Exception ex) {
                logger.error("An unexpected error occurs while loading", ex);
                return Single.error(ex);
            }
        } else {
            logger.error("No identity provider is registered for type {}", providerConfiguration.getType());
            return Single.error(new IllegalStateException("No identity provider is registered for type " + providerConfiguration.getType()));
        }
    }
}
