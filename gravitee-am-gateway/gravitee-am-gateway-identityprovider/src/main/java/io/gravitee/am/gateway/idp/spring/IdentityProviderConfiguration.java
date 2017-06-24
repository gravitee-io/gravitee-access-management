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
package io.gravitee.am.gateway.idp.spring;

import io.gravitee.am.gateway.idp.core.IdentityProviderConfigurationFactory;
import io.gravitee.am.gateway.idp.core.IdentityProviderMapperFactory;
import io.gravitee.am.gateway.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.gateway.idp.core.IdentityProviderRoleMapperFactory;
import io.gravitee.am.gateway.idp.core.impl.IdentityProviderConfigurationFactoryImpl;
import io.gravitee.am.gateway.idp.core.impl.IdentityProviderMapperFactoryImpl;
import io.gravitee.am.gateway.idp.core.impl.IdentityProviderPluginManagerImpl;
import io.gravitee.am.gateway.idp.core.impl.IdentityProviderRoleMapperFactoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class IdentityProviderConfiguration {

    @Bean
    public IdentityProviderPluginManager identityProviderPluginManager() {
        return new IdentityProviderPluginManagerImpl();
    }

    @Bean
    public IdentityProviderConfigurationFactory identityProviderConfigurationFactory() {
        return new IdentityProviderConfigurationFactoryImpl();
    }

    @Bean
    public IdentityProviderMapperFactory identityProviderMapperFactory() {
        return new IdentityProviderMapperFactoryImpl();
    }

    @Bean
    public IdentityProviderRoleMapperFactory identityProviderRoleMapperFactory() {
        return new IdentityProviderRoleMapperFactoryImpl();
    }
}
