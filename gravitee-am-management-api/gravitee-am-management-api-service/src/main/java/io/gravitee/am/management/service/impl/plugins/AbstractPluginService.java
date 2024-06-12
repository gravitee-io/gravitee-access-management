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
package io.gravitee.am.management.service.impl.plugins;

import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class AbstractPluginService {
    protected AmPluginManager pluginManager;

    protected AbstractPluginService(AmPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public Completable checkPluginDeployment(String type) {
        if (!this.pluginManager.isPluginDeployed(type)) {
            log.debug("Plugin {} not deployed", type);
            return Completable.error(PluginNotDeployedException.forType(type));
        }
        return Completable.complete();
    }
}
