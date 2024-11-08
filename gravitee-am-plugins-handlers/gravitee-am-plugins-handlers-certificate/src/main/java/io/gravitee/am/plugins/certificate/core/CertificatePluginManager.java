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
package io.gravitee.am.plugins.certificate.core;

import io.gravitee.am.certificate.api.Certificate;
import io.gravitee.am.certificate.api.CertificateConfiguration;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificatePluginManager
        extends ProviderPluginManager<Certificate<?, CertificateProvider>, CertificateProvider, CertificateProviderConfiguration>
        implements AmPluginManager<Certificate<?, CertificateProvider>> {

    private final Logger logger = LoggerFactory.getLogger(CertificatePluginManager.class);

    private final ConfigurationFactory<CertificateConfiguration> configurationFactory;

    public CertificatePluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<CertificateConfiguration> configurationFactory
    ) {
        super(pluginContextFactory);
        this.configurationFactory = configurationFactory;
    }

    @Override
    public CertificateProvider create(CertificateProviderConfiguration providerConfig) {
        logger.debug("Looking for a certificate provider for [{}]", providerConfig.getType());
        var certificatePlugin = ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No certificate provider is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No certificate provider is registered for type " + providerConfig.getType());
        });

        var certificateConfiguration = configurationFactory.create(certificatePlugin.configuration(), providerConfig.getConfiguration());
        return createProvider(certificatePlugin, List.of(
                new CertificateConfigurationBeanFactoryPostProcessor(certificateConfiguration),
                new CertificateMetadataBeanFactoryPostProcessor(getCertificateMetadata(providerConfig))
        ));
    }

    private static CertificateMetadata getCertificateMetadata(CertificateProviderConfiguration providerConfig) {
        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(providerConfig.getMetadata());
        return certificateMetadata;
    }

    private static class CertificateMetadataBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<CertificateMetadata> {
        private CertificateMetadataBeanFactoryPostProcessor(CertificateMetadata metadata) {
            super("metadata", metadata);
        }
    }

    private static class CertificateConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<CertificateConfiguration> {
        private CertificateConfigurationBeanFactoryPostProcessor(CertificateConfiguration configuration) {
            super("configuration", configuration);
        }
    }

}
