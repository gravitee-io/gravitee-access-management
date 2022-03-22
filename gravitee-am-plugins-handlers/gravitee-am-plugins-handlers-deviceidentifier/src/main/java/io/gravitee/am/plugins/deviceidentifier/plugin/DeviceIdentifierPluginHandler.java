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
package io.gravitee.am.plugins.deviceidentifier.plugin;

import io.gravitee.am.deviceidentifier.api.DeviceIdentifier;
import io.gravitee.am.plugins.handlers.api.plugin.AmPluginHandler;
import io.gravitee.plugin.core.api.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Rémi Sultan  (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierPluginHandler extends AmPluginHandler<DeviceIdentifier> {

    private final static Logger LOGGER = LoggerFactory.getLogger(DeviceIdentifierPluginHandler.class);
    public static final String PLUGIN_TYPE_DEVICE_IDENTIFIER = "DEVICE_IDENTIFIER";

    @Override
    public boolean canHandle(Plugin plugin) {
        return type().equalsIgnoreCase(plugin.type());
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    protected Class<DeviceIdentifier> getClazz() {
        return DeviceIdentifier.class;
    }

    @Override
    protected String type() {
        return PLUGIN_TYPE_DEVICE_IDENTIFIER;
    }
}
