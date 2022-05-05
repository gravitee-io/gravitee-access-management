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
package io.gravitee.am.plugins.reporter.plugin;

import io.gravitee.am.plugins.handlers.api.plugin.AmPluginHandler;
import io.gravitee.am.reporter.api.Reporter;
import io.gravitee.plugin.core.api.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ReporterPluginHandler extends AmPluginHandler<Reporter> {

    private static final String AM_REPORTER_PLUGIN_TYPE = "am-reporter";
    private final Logger LOGGER = LoggerFactory.getLogger(ReporterPluginHandler.class);

    @Autowired
    private ApplicationContext appContext;

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    protected Class<Reporter> getClazz() {
        return Reporter.class;
    }

    @Override
    protected String type() {
        return AM_REPORTER_PLUGIN_TYPE;
    }

    @Override
    protected ClassLoader getClassLoader(Plugin plugin) {
        return pluginClassLoaderFactory.getOrCreateClassLoader(plugin, appContext.getBean("ConnectionProviderFromRepository").getClass().getClassLoader());
    }
}
