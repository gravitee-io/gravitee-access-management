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
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.plugin.core.api.PluginContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ReporterPluginManager
        extends ProviderPluginManager<Reporter<?, AuditReporter>, AuditReporter, ReporterProviderConfiguration>
        implements AmPluginManager<Reporter<?, AuditReporter>> {

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
    public AuditReporter create(ReporterProviderConfiguration providerConfig) {
        logger.debug("Looking for an reporter provider for [{}]", providerConfig.getType());
        Reporter<?, AuditReporter> reporter = ofNullable(get(providerConfig.getType())).orElseGet(() -> {
            logger.error("No reporter provider is registered for type {}", providerConfig.getType());
            throw new IllegalStateException("No reporter provider is registered for type " + providerConfig.getType());
        });

        var reporterConfiguration = reporterConfigurationFactory.create(reporter.configuration(), providerConfig.getConfiguration());
        var context = providerConfig.getGraviteeContext();
        var postProcessors = ofNullable(context).map(ctx -> List.of(
                new ReporterConfigurationBeanFactoryPostProcessor(reporterConfiguration),
                new ReporterBeanFactoryPostProcessor(providerConfig.getReporter()),
                new GraviteeContextBeanFactoryPostProcessor(context)
        )).orElse(List.of(new ReporterConfigurationBeanFactoryPostProcessor(reporterConfiguration)));

        return createProvider(reporter, postProcessors);
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

    private static class ReporterBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<io.gravitee.am.model.Reporter> {
        private ReporterBeanFactoryPostProcessor(io.gravitee.am.model.Reporter reporter) {
            super("reporterDefinition", reporter);
        }
    }

}
