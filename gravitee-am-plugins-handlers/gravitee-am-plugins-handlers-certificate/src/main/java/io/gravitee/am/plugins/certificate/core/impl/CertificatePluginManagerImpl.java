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
package io.gravitee.am.plugins.certificate.core.impl;

import io.gravitee.am.certificate.api.Certificate;
import io.gravitee.am.certificate.api.CertificateConfiguration;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.plugins.certificate.core.CertificateProviderConfiguration;
import io.gravitee.am.plugins.handlers.api.core.AMPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.plugin.core.api.PluginContextFactory;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificatePluginManagerImpl extends CertificatePluginManager  {

    private final Logger logger = LoggerFactory.getLogger(CertificatePluginManagerImpl.class);

    private final ConfigurationFactory<CertificateConfiguration> certificateConfigurationFactory;

    @Autowired
    public CertificatePluginManagerImpl(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<CertificateConfiguration> certificateConfigurationFactory
    ) {
        super(pluginContextFactory);
        this.certificateConfigurationFactory = certificateConfigurationFactory;
    }

    @Override
    public CertificateProvider create(CertificateProviderConfiguration providerConfig) {
        logger.debug("Looking for a certificate provider for [{}]", providerConfig.getType());
        Certificate certificate = instances.get(providerConfig.getType());

        if (certificate != null) {
            Class<? extends CertificateConfiguration> configurationClass = certificate.configuration();
            var certificateConfiguration = certificateConfigurationFactory.create(configurationClass, providerConfig.getConfiguration());

            CertificateMetadata certificateMetadata = new CertificateMetadata();
            certificateMetadata.setMetadata(providerConfig.getMetadata());

            return createProvider(
                    plugins.get(certificate),
                    certificate.certificateProvider(),
                    List.of(
                            new CertificateConfigurationBeanFactoryPostProcessor(certificateConfiguration),
                            new CertificateMetadataBeanFactoryPostProcessor(certificateMetadata)
                    )
            );
        } else {
            logger.error("No certificate provider is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No certificate provider is registered for type " + providerConfig.getType());
        }
    }
}
