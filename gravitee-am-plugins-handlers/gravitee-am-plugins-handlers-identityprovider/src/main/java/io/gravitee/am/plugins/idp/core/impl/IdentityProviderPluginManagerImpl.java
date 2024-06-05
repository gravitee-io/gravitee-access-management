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
import io.gravitee.am.identityprovider.api.NoIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.plugins.handlers.api.core.AmPluginContextConfigurer;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.plugins.idp.core.AuthenticationProviderConfiguration;
import io.gravitee.am.plugins.idp.core.IdentityProviderMapperFactory;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.plugins.idp.core.IdentityProviderRoleMapperFactory;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderPluginManagerImpl extends IdentityProviderPluginManager {

    private final Logger logger = LoggerFactory.getLogger(IdentityProviderPluginManagerImpl.class);

    private final ConfigurationFactory<IdentityProviderConfiguration> configurationFactory;
    private final IdentityProviderMapperFactory mapperFactory;
    private final IdentityProviderRoleMapperFactory roleMapperFactory;
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
        this.configurationFactory = identityProviderConfigurationFactory;
        this.mapperFactory = identityProviderMapperFactory;
        this.roleMapperFactory = identityProviderRoleMapperFactory;
        this.graviteeProperties = graviteeProperties;
        this.vertx = vertx;
    }

    @Override
    public boolean hasUserProvider(String pluginType) {
        logger.debug("Looking for an user provider for [{}]", pluginType);
        return ofNullable(get(pluginType)).map(IdentityProvider::userProvider).isPresent();
    }

    @Override
    public AuthenticationProvider create(AuthenticationProviderConfiguration providerConfig) {
        logger.debug("Looking for an authentication provider for [{}]", providerConfig.getType());
        var identityProvider = ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No identity provider is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No identity provider is registered for type " + providerConfig.getType());
        });

        return createProvider(identityProvider, getBeanFactoryPostProcessors(providerConfig, identityProvider));
    }

    private List<? extends BeanFactoryPostProcessor> getBeanFactoryPostProcessors(
            AuthenticationProviderConfiguration providerConfig,
            IdentityProvider<?, AuthenticationProvider> identityProvider) {
        var identityProviderConfiguration = configurationFactory.create(identityProvider.configuration(), providerConfig.getConfiguration());
        var identityProviderMapper = mapperFactory.create(identityProvider.mapper(), providerConfig.getMappers());
        var identityProviderRoleMapper = roleMapperFactory.create(identityProvider.roleMapper(), providerConfig.getRoleMapper());

        var postProcessors = Stream.of(
                new IdentityProviderConfigurationBeanFactoryPostProcessor(identityProviderConfiguration),
                new IdentityProviderMapperBeanFactoryPostProcessor(identityProviderMapper),
                new IdentityProviderRoleMapperBeanFactoryPostProcessor(identityProviderRoleMapper),
                new PropertiesBeanFactoryPostProcessor(graviteeProperties),
                new VertxBeanFactoryPostProcessor(vertx),
                new IdentityProviderEntityBeanFactoryPostProcessor(providerConfig.getIdentityProvider())
        ).collect(toList());

        if (nonNull(providerConfig.getCertificateManager())) {
            postProcessors.add(new CertificateManagerBeanFactoryPostProcessor(providerConfig.getCertificateManager()));
        }

        return postProcessors;
    }

    @Override
    public Single<Optional<UserProvider>> create(String type, String configuration, io.gravitee.am.model.IdentityProvider identityProviderEntity) {
        logger.debug("Looking for an user provider for [{}]", type);
        var providerConfig = new ProviderConfiguration(type, configuration);
        var identityProvider = get(providerConfig.getType());

        if (nonNull(identityProvider)) {
            var identityProviderConfiguration = configurationFactory.create(identityProvider.configuration(), providerConfig.getConfiguration());

            if (isNull(identityProvider.userProvider())|| !identityProviderConfiguration.userProvider()) {
                logger.info("No user provider is registered for type {}", providerConfig.getType());
                return Single.just(Optional.empty());
            }

            var identityProviderMapper = mapperFactory.create(identityProvider.mapper(), identityProviderEntity.getMappers());

            try {
                return createUserProvider(new AmPluginContextConfigurer<>(
                        identityProvider,
                        identityProvider.userProvider(),
                        List.of(
                                new IdentityProviderConfigurationBeanFactoryPostProcessor(identityProviderConfiguration),
                                new PropertiesBeanFactoryPostProcessor(graviteeProperties),
                                new VertxBeanFactoryPostProcessor(vertx),
                                new IdentityProviderEntityBeanFactoryPostProcessor(identityProviderEntity),
                                new IdentityProviderMapperBeanFactoryPostProcessor(ofNullable(identityProviderMapper).orElse(new NoIdentityProviderMapper()))
                        ))
                ).map(Optional::of);
            } catch (Exception ex) {
                logger.error("An unexpected error occurs while loading", ex);
                return Single.error(ex);
            }
        } else {
            logger.error("No identity provider is registered for type {}", providerConfig.getType());
            return Single.error(new IllegalStateException("No identity provider is registered for type " + providerConfig.getType()));
        }
    }
}
