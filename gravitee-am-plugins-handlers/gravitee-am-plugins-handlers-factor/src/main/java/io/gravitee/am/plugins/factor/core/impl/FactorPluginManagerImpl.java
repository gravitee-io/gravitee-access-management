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
package io.gravitee.am.plugins.factor.core.impl;

import io.gravitee.am.factor.api.Factor;
import io.gravitee.am.factor.api.FactorConfiguration;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.plugins.factor.core.FactorConfigurationFactory;
import io.gravitee.am.plugins.factor.core.FactorDefinition;
import io.gravitee.am.plugins.factor.core.FactorPluginManager;
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
public class FactorPluginManagerImpl implements FactorPluginManager {

    private final Logger logger = LoggerFactory.getLogger(FactorPluginManagerImpl.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";

    private final Map<String, Factor> factors = new HashMap<>();
    private final Map<Factor, Plugin> factorPlugins = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private FactorConfigurationFactory factorConfigurationFactory;

    @Override
    public void register(FactorDefinition factorDefinition) {
        factors.putIfAbsent(factorDefinition.getPlugin().id(),
                factorDefinition.getFactor());

        factorPlugins.putIfAbsent(factorDefinition.getFactor(),
                factorDefinition.getPlugin());
    }

    @Override
    public Collection<Plugin> getAll() {
        return factorPlugins.values();
    }

    @Override
    public Plugin findById(String factorId) {
        Factor factor = factors.get(factorId);
        return factor != null ? factorPlugins.get(factor) : null;
    }

    @Override
    public FactorProvider create(String type, String configuration) {
        logger.debug("Looking for a factor for [{}]", type);
        Factor factor = factors.get(type);

        if (factor != null) {
            Class<? extends FactorConfiguration> configurationClass = factor.configuration();
            FactorConfiguration factorConfiguration = factorConfigurationFactory.create(configurationClass, configuration);

            return create0(
                    factorPlugins.get(factor),
                    factor.factorProvider(),
                    factorConfiguration);
        } else {
            logger.error("No factor is registered for type {}", type);
            throw new IllegalStateException("No factor is registered for type " + type);
        }
    }

    @Override
    public String getSchema(String factorId) throws IOException {
        Factor factor = factors.get(factorId);
        Path policyWorkspace = factorPlugins.get(factor).path();

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

    private <T> T create0(Plugin plugin, Class<T> identityClass, FactorConfiguration factorConfiguration) {
        if (identityClass == null) {
            return null;
        }

        try {
            T identityObj = createInstance(identityClass);
            final Import annImport = identityClass.getAnnotation(Import.class);
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

                    // Add authenticator configuration bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new FactorConfigurationBeanFactoryPostProcessor(factorConfiguration));

                    return configurableApplicationContext;
                }
            });

            idpApplicationContext.getAutowireCapableBeanFactory().autowireBean(identityObj);

            if (identityObj instanceof InitializingBean) {
                ((InitializingBean) identityObj).afterPropertiesSet();
            }

            return identityObj;
        } catch (Exception ex) {
            logger.error("An unexpected error occurs while loading factor", ex);
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
