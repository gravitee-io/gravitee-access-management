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
package io.gravitee.am.plugins.certificate.spring;

import io.gravitee.am.certificate.api.CertificateConfiguration;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.plugins.handlers.api.core.impl.ConfigurationFactoryImpl;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class CertificateSpringConfiguration {

    @Bean
    public CertificatePluginManager certificatePluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<CertificateConfiguration> botDetectionConfigurationFactory
    ) {
        return new CertificatePluginManager(pluginContextFactory, botDetectionConfigurationFactory);
    }

    @Bean
    public ConfigurationFactory<CertificateConfiguration> certificateConfigurationFactory() {
        return new ConfigurationFactoryImpl<>();
    }

    @Bean
    public PluginConfigurationValidatorsRegistry certificateValidatorsRegistry(){
        return new PluginConfigurationValidatorsRegistry();
    }

}
