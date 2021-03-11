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
package io.gravitee.am.plugins.notifier.core;

import io.gravitee.plugin.core.api.ConfigurablePluginManager;
import io.gravitee.plugin.core.api.Plugin;
import io.gravitee.plugin.core.internal.PluginManifestProperties;
import io.gravitee.plugin.notifier.NotifierPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NotifierPluginManager {

    @Autowired
    private ConfigurablePluginManager<NotifierPlugin> pluginManager;

    private boolean alertPluginEnabled;

    public Collection<NotifierPlugin> findAll() {
        return pluginManager.findAll();
    }

    public NotifierPlugin findById(String notifierId) {
        return pluginManager.get(notifierId);
    }

    public String getSchema(String notifierId) throws IOException {
        return pluginManager.getSchema(notifierId);
    }

    public String getIcon(String notifierId) throws IOException {
        return pluginManager.getIcon(notifierId);
    }

    public String getDocumentation(String notifierId) throws IOException {
        return pluginManager.getDocumentation(notifierId);
    }

    public void setAlertPluginEnabled(boolean alertPluginEnabled) {

        this.alertPluginEnabled = alertPluginEnabled;
    }

    public boolean isAlertPluginEnabled() {
        return alertPluginEnabled;
    }
}
