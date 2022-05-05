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
package io.gravitee.am.plugins.reporter.core;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.ConfigurationFactory;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.am.reporter.api.Reporter;
import io.gravitee.am.reporter.api.ReporterConfiguration;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ReporterPluginManager
        extends ProviderPluginManager<Reporter, io.gravitee.am.reporter.api.provider.Reporter, ReporterProviderConfiguration>
        implements AmPluginManager<Reporter> {

    private final Logger logger = LoggerFactory.getLogger(ReporterPluginManager.class);

    private final ConfigurationFactory<ReporterConfiguration> reporterConfigurationFactory;

    public ReporterPluginManager(
            PluginContextFactory pluginContextFactory,
            ConfigurationFactory<ReporterConfiguration> reporterConfigurationFactory
    ) {
        super(pluginContextFactory);
        this.reporterConfigurationFactory = reporterConfigurationFactory;
    }

    @Override
    public io.gravitee.am.reporter.api.provider.Reporter create(ReporterProviderConfiguration providerConfiguration) {
        logger.debug("Looking for an reporter provider for [{}]", providerConfiguration.getType());
        Reporter reporter = instances.get(providerConfiguration.getType());

        if (reporter != null) {
            Class<? extends ReporterConfiguration> configurationClass = reporter.configuration();
            var reporterConfiguration = reporterConfigurationFactory.create(configurationClass, providerConfiguration.getConfiguration());
            if (providerConfiguration.getGraviteeContext() != null) {
                return createProvider(plugins.get(reporter), reporter.auditReporter(), List.of(
                                new ReporterConfigurationBeanFactoryPostProcessor(reporterConfiguration),
                                new GraviteeContextBeanFactoryPostProcessor(providerConfiguration.getGraviteeContext())
                        )
                );
            }
            return createProvider(plugins.get(reporter), reporter.auditReporter(),
                    List.of(new ReporterConfigurationBeanFactoryPostProcessor(reporterConfiguration))
            );
        } else {
            logger.error("No reporter provider is registered for type {}", providerConfiguration.getType());
            throw new IllegalStateException("No reporter provider is registered for type " + providerConfiguration.getType());
        }
    }


    private static class GraviteeContextBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<GraviteeContext> {
        private GraviteeContextBeanFactoryPostProcessor(GraviteeContext context) {
            super("graviteeContext", context);
        }
    }

    private static class ReporterConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<ReporterConfiguration> {
        private ReporterConfigurationBeanFactoryPostProcessor(ReporterConfiguration configuration) {
            super("configuration", configuration);
        }
    }

}
