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
package io.gravitee.am.plugins.botdetection.plugin;

import io.gravitee.am.botdetection.api.BotDetection;
import io.gravitee.am.plugins.handlers.api.plugin.AmPluginHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BotDetectionPluginHandler extends AmPluginHandler<BotDetection> {

    private final Logger LOGGER = LoggerFactory.getLogger(BotDetectionPluginHandler.class);

    public static final String PLUGIN_TYPE_BOT_DETECTION = "BOT_DETECTION";

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    protected Class<BotDetection> getClazz() {
        return BotDetection.class;
    }

    @Override
    protected String type() {
        return PLUGIN_TYPE_BOT_DETECTION;
    }
}
