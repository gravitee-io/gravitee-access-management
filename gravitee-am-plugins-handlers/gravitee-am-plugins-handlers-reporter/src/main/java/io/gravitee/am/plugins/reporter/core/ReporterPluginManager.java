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
import io.gravitee.am.plugins.handlers.api.core.AMPluginManager;
import io.gravitee.am.plugins.handlers.api.core.NamedBeanFactoryPostProcessor;
import io.gravitee.am.plugins.handlers.api.core.ProviderPluginManager;
import io.gravitee.am.reporter.api.Reporter;
import io.gravitee.am.reporter.api.ReporterConfiguration;
import io.gravitee.plugin.core.api.PluginContextFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class ReporterPluginManager
        extends ProviderPluginManager<Reporter, io.gravitee.am.reporter.api.provider.Reporter, ReporterProviderConfiguration>
        implements AMPluginManager<Reporter> {

    protected ReporterPluginManager(PluginContextFactory pluginContextFactory) {
        super(pluginContextFactory);
    }


    protected static class GraviteeContextBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<GraviteeContext> {
        public GraviteeContextBeanFactoryPostProcessor(GraviteeContext context) {
            super("graviteeContext", context);
        }
    }

    protected static class ReporterConfigurationBeanFactoryPostProcessor extends NamedBeanFactoryPostProcessor<ReporterConfiguration> {
        public ReporterConfigurationBeanFactoryPostProcessor(ReporterConfiguration configuration) {
            super("configuration", configuration);
        }
    }
}
