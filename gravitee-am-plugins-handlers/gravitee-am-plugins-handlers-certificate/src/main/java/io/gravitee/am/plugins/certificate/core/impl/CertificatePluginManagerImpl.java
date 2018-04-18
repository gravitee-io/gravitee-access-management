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
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.plugins.certificate.core.CertificateConfigurationFactory;
import io.gravitee.am.plugins.certificate.core.CertificateDefinition;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.api.PluginContextFactory;
import io.gravitee.plugin.core.internal.AnnotationBasedPluginContextConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificatePluginManagerImpl implements CertificatePluginManager {

    private final Logger logger = LoggerFactory.getLogger(CertificatePluginManagerImpl.class);
    private final static String SCHEMAS_DIRECTORY = "schemas";
    private final Map<String, Certificate> certificates = new HashMap<>();
    private final Map<Certificate, Plugin> certificatePlugins = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private CertificateConfigurationFactory certificateConfigurationFactory;

    @Override
    public void register(CertificateDefinition certificatePluginDefinition) {
        certificates.putIfAbsent(certificatePluginDefinition.getPlugin().id(),
                certificatePluginDefinition.getCertificate());

        certificatePlugins.putIfAbsent(certificatePluginDefinition.getCertificate(),
                certificatePluginDefinition.getPlugin());
    }

    @Override
    public Collection<Plugin> getAll() {
        return certificatePlugins.values();
    }

    @Override
    public Plugin findById(String certificateId) {
        Certificate certificate = certificates.get(certificateId);
        return (certificate != null) ? certificatePlugins.get(certificate) : null;
    }

    @Override
    public CertificateProvider create(String type, String configuration) {
        logger.debug("Looking for a certificate provider for [{}]", type);
        Certificate certificate = certificates.get(type);

        if (certificate != null) {
            Class<? extends CertificateConfiguration> configurationClass = certificate.configuration();
            CertificateConfiguration certificateConfiguration = certificateConfigurationFactory.create(configurationClass, configuration);

            return create0(
                    certificatePlugins.get(certificate),
                    certificate.certificateProvider(),
                    certificateConfiguration);
        } else {
            logger.error("No certificate provider is registered for type {}", type);
            throw new IllegalStateException("No certificate provider is registered for type " + type);
        }
    }

    @Override
    public String getSchema(String certificateId) throws IOException {
        Certificate certificate = certificates.get(certificateId);
        Path policyWorkspace = certificatePlugins.get(certificate).path();

        File[] schemas = policyWorkspace.toFile().listFiles(
                pathname -> pathname.isDirectory() && pathname.getName().equals(SCHEMAS_DIRECTORY));

        if (schemas.length == 1) {
            File schemaDir = schemas[0];

            if (schemaDir.listFiles().length > 0) {
                return new String(Files.readAllBytes(schemaDir.listFiles()[0].toPath()));
            }
        }

        return null;
    }

    private <T> T create0(Plugin plugin, Class<T> certificateClass, CertificateConfiguration certificateConfiguration) {
        if (certificateClass == null) {
            return null;
        }

        try {
            T certificateObj = createInstance(certificateClass);
            final Import annImport = certificateClass.getAnnotation(Import.class);
            Set<Class<?>> configurations = (annImport != null) ?
                    new HashSet<>(Arrays.asList(annImport.value())) : Collections.emptySet();

            ApplicationContext idpApplicationContext = pluginContextFactory.create(new AnnotationBasedPluginContextConfigurer(plugin) {
                @Override
                public Set<Class<?>> configurations() {
                    return configurations;
                }

                @Override
                public ConfigurableApplicationContext applicationContext() {
                    ConfigurableApplicationContext configurableApplicationContext = super.applicationContext();

                    // Add certificate configuration bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new CertificateConfigurationBeanFactoryPostProcessor(certificateConfiguration));

                    return configurableApplicationContext;
                }
            });

            idpApplicationContext.getAutowireCapableBeanFactory().autowireBean(certificateObj);

            if (certificateObj instanceof InitializingBean) {
                ((InitializingBean) certificateObj).afterPropertiesSet();
            }

            return certificateObj;
        } catch (Exception ex) {
            logger.error("An unexpected error occurs while loading certificate", ex);
            return null;
        }
    }

    private <T> T createInstance(Class<T> clazz) throws Exception {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error("Unable to instantiate class: {}", ex);
            throw ex;
        }
    }
}
