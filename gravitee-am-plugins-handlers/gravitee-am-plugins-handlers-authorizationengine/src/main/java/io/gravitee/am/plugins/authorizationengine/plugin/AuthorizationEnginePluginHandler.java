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
package io.gravitee.am.plugins.authorizationengine.plugin;

import io.gravitee.am.authorizationengine.api.AuthorizationEngine;
import io.gravitee.am.plugins.handlers.api.plugin.AmPluginHandler;
import io.gravitee.plugin.core.api.Plugin;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author GraviteeSource Team
 */
@Slf4j
public class AuthorizationEnginePluginHandler extends AmPluginHandler<AuthorizationEngine<?, ?>>  {

    private final Logger LOGGER = LoggerFactory.getLogger(AuthorizationEngine.class);

    private static final String PLUGIN_TYPE = "authorization-engine";

    @Override
    public boolean canHandle(Plugin plugin) {
        return type().equalsIgnoreCase(plugin.type());
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    protected String type() {
        return PLUGIN_TYPE;
    }
}
