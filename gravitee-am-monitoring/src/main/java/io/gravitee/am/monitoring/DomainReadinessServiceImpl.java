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
package io.gravitee.am.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author GraviteeSource Team
 */
public class DomainReadinessServiceImpl implements DomainReadinessService {

    private final Logger logger = LoggerFactory.getLogger(DomainReadinessServiceImpl.class);

    private final Map<String, DomainState> domainStates = new ConcurrentHashMap<>();

    @Override
    public DomainState getDomainState(String domainId) {
        return domainStates.get(domainId);
    }

    @Override
    public void initPluginSync(String domainId, String pluginId, String pluginType) {
        if (domainId != null) {
            logger.debug("[{}] Init Plugin {} synchronization for domain {}", pluginType, pluginId, domainId);
            domainStates.computeIfAbsent(domainId, k -> new DomainState()).initPluginSync(pluginId, pluginType);
        }
    }

    @Override
    public void pluginLoaded(String domainId, String pluginId) {
        if (domainId != null) {
            DomainState domainState = domainStates.get(domainId);
            if (domainState == null || !domainState.getCreationState().containsKey(pluginId)) {
                logger.error("Plugin {} loaded for domain {} but was not initialized", pluginId, domainId);
                return;
            }
            String pluginType = domainState.getCreationState().get(pluginId).getType();
            logger.debug("[{}] Plugin {} loaded for domain {}", pluginType, pluginId, domainId);
            updatePluginStatus(domainId, pluginId, true, null);
        }
    }

    @Override
    public void pluginFailed(String domainId, String pluginId, String message) {
        if (domainId != null) {
            DomainState domainState = domainStates.get(domainId);
            if (domainState == null || !domainState.getCreationState().containsKey(pluginId)) {
                logger.error("Plugin {} failed to load for domain {} but was not initialized - error:{}", pluginId, domainId, message);
                return;
            }
            String pluginType = domainState.getCreationState().get(pluginId).getType();
            logger.debug("[{}] Plugin {} failed to load for domain {} - error:{}", pluginType, pluginId, domainId, message);
            updatePluginStatus(domainId, pluginId, false, message);
        }
    }

    @Override
    public void pluginUnloaded(String domainId, String pluginId) {
        if (domainId != null) {
            DomainState domainState = domainStates.get(domainId);
            if (domainState != null && domainState.getCreationState().containsKey(pluginId)) {
                String pluginType = domainState.getCreationState().get(pluginId).getType();
                logger.debug("[{}] Plugin {} unloaded for domain {}", pluginType, pluginId, domainId);
            } else {
                logger.debug("Plugin {} unloaded for domain {}", pluginId, domainId);
            }
            domainStates.computeIfAbsent(domainId, k -> new DomainState()).removePlugin(pluginId);
        }
    }

    @Override
    public void updateDomainStatus(String domainId, DomainState.Status status) {
        if (domainId != null) {
            logger.debug("Updates domain status for domain {}, status {}", domainId, status);
            domainStates.computeIfAbsent(domainId, k -> new DomainState()).setStatus(status).setLastSync(System.currentTimeMillis());
        }
    }

    @Override
    public void removeDomain(String domainId) {
        if (domainId != null) {
            logger.debug("Removing domain state for {}", domainId);
            domainStates.remove(domainId);
        }
    }

    private void updatePluginStatus(String domainId, String pluginId, boolean success, String message) {
        domainStates.computeIfAbsent(domainId, k -> new DomainState()).updatePluginState(pluginId, success, message);
    }
}
