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
package io.gravitee.am.plugins.idp.spring;

import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationEvaluatorsRegistry;
import io.gravitee.am.plugins.handlers.api.core.impl.EvaluatedConfigurationFactoryImpl;
import io.gravitee.am.plugins.idp.core.IdentityProviderGroupMapperFactory;
import io.gravitee.am.plugins.idp.core.IdentityProviderMapperFactory;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.plugins.idp.core.IdentityProviderRoleMapperFactory;
import io.gravitee.am.plugins.idp.core.impl.IdentityProviderGroupMapperFactoryImpl;
import io.gravitee.am.plugins.idp.core.impl.IdentityProviderMapperFactoryImpl;
import io.gravitee.am.plugins.idp.core.impl.IdentityProviderPluginManagerImpl;
import io.gravitee.am.plugins.idp.core.impl.IdentityProviderRoleMapperFactoryImpl;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.vertx.rxjava3.core.Vertx;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class IdentityProviderSpringConfiguration {

    @Bean
    public IdentityProviderPluginManager identityProviderPluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<IdentityProviderConfiguration> identityProviderConfigurationFactory,
            IdentityProviderMapperFactory identityProviderMapperFactory,
            IdentityProviderRoleMapperFactory identityProviderRoleMapperFactory,
            IdentityProviderGroupMapperFactory identityProviderGroupMapperFactory,
            @Qualifier("graviteeProperties") Properties graviteeProperties,
            Vertx vertx
    ) {
        return new IdentityProviderPluginManagerImpl(
                pluginContextFactory,
                identityProviderConfigurationFactory,
                identityProviderMapperFactory,
                identityProviderRoleMapperFactory,
                identityProviderGroupMapperFactory,
                graviteeProperties,
                vertx
        );
    }

    @Bean
    public ConfigurationFactory<IdentityProviderConfiguration> identityProviderConfigurationFactory(
            PluginConfigurationEvaluatorsRegistry evaluatorsRegistry
    ) {
        return new EvaluatedConfigurationFactoryImpl<>(evaluatorsRegistry.getEvaluators());
    }

    @Bean
    public IdentityProviderMapperFactory identityProviderMapperFactory() {
        return new IdentityProviderMapperFactoryImpl();
    }

    @Bean
    public IdentityProviderRoleMapperFactory identityProviderRoleMapperFactory() {
        return new IdentityProviderRoleMapperFactoryImpl();
    }

    @Bean
    public IdentityProviderGroupMapperFactory identityProviderGroupMapperFactory() {
        return new IdentityProviderGroupMapperFactoryImpl();
    }

}
