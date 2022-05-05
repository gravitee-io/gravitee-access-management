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
package io.gravitee.am.plugins.reporter.core.impl;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.plugins.reporter.core.ReporterConfigurationFactory;
import io.gravitee.am.plugins.reporter.core.ReporterDefinition;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.reporter.api.Reporter;
import io.gravitee.am.reporter.api.ReporterConfiguration;
import io.gravitee.common.service.AbstractService;
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
public class ReporterPluginManagerImpl implements ReporterPluginManager {

    private final Logger logger = LoggerFactory.getLogger(ReporterPluginManagerImpl.class);

    private final static String SCHEMAS_DIRECTORY = "schemas";
    private final Map<String, Reporter> reporters = new HashMap<>();
    private final Map<Reporter, Plugin> reporterPlugins = new HashMap<>();

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private ReporterConfigurationFactory reporterConfigurationFactory;

    @Override
    public void register(ReporterDefinition reporterDefinition) {
        reporters.putIfAbsent(reporterDefinition.getPlugin().id(),
                reporterDefinition.getReporter());

        reporterPlugins.putIfAbsent(reporterDefinition.getReporter(),
                reporterDefinition.getPlugin());
    }

    @Override
    public Collection<Plugin> getAll() {
        return reporterPlugins.values();
    }

    @Override
    public Plugin findById(String reporterId) {
        Reporter reporter = reporters.get(reporterId);
        return (reporter != null) ? reporterPlugins.get(reporter) : null;
    }

    @Override
    public String getSchema(String reporterId) throws IOException {
        Reporter reporter = reporters.get(reporterId);
        if (reporterPlugins.containsKey(reporter)) {
            Path policyWorkspace = reporterPlugins.get(reporter).path();

            File[] schemas = policyWorkspace.toFile().listFiles(
                    pathname -> pathname.isDirectory() && pathname.getName().equals(SCHEMAS_DIRECTORY));

            if (schemas.length == 1) {
                File schemaDir = schemas[0];

                if (schemaDir.listFiles().length > 0) {
                    return new String(Files.readAllBytes(schemaDir.listFiles()[0].toPath()));
                }
            }
        }
        return null;
    }

    @Override
    public io.gravitee.am.reporter.api.provider.Reporter create(String type, String configuration, GraviteeContext context) {
        logger.debug("Looking for an reporter provider for [{}]", type);
        Reporter reporter = reporters.get(type);

        if (reporter != null) {
            Class<? extends ReporterConfiguration> configurationClass = reporter.configuration();
            ReporterConfiguration reporterConfiguration = reporterConfigurationFactory.create(configurationClass, configuration);

            return create0(reporterPlugins.get(reporter),
                    reporter.auditReporter(),
                    reporterConfiguration,
                    context);
        } else {
            logger.error("No reporter provider is registered for type {}", type);
            throw new IllegalStateException("No reporter provider is registered for type " + type);
        }
    }


    private <T> T create0(Plugin plugin, Class<T> auditReporterClass, ReporterConfiguration reporterConfiguration, GraviteeContext context) {
        if (auditReporterClass == null) {
            return null;
        }

        try {
            T auditReporterObj = createInstance(auditReporterClass);
            final Import annImport = auditReporterClass.getAnnotation(Import.class);
            Set<Class<?>> configurations = (annImport != null) ?
                    new HashSet<>(Arrays.asList(annImport.value())) : Collections.emptySet();

            ApplicationContext reporterApplicationContext = pluginContextFactory.create(new AnnotationBasedPluginContextConfigurer(plugin) {
                @Override
                public Set<Class<?>> configurations() {
                    return configurations;
                }

                @Override
                public ConfigurableApplicationContext applicationContext() {
                    ConfigurableApplicationContext configurableApplicationContext = super.applicationContext();

                    // Add reporter configuration bean
                    configurableApplicationContext.addBeanFactoryPostProcessor(
                            new ReporterConfigurationBeanFactoryPostProcessor(reporterConfiguration));

                    // Add gravitee context bean to provide execution context information to the reporter.
                    // this is useful for some reporter like the file-reporter
                    if (context != null) {
                        configurableApplicationContext.addBeanFactoryPostProcessor(
                                new GraviteeContextBeanFactoryPostProcessor(context));
                    }

                    return configurableApplicationContext;
                }
            });

            if (auditReporterObj instanceof AbstractService) {
                ((AbstractService<?>) auditReporterObj).setApplicationContext(reporterApplicationContext);
            }
            reporterApplicationContext.getAutowireCapableBeanFactory().autowireBean(auditReporterObj);
            if (auditReporterObj instanceof InitializingBean) {
                ((InitializingBean) auditReporterObj).afterPropertiesSet();
            }

            return auditReporterObj;
        } catch (Exception ex) {
            logger.error("An unexpected error occurs while loading reporter", ex);
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
