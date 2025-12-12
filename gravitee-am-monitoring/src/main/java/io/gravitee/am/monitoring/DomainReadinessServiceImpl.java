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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author GraviteeSource Team
 */
public class DomainReadinessServiceImpl implements DomainReadinessService {

    private final Logger logger = LoggerFactory.getLogger(DomainReadinessServiceImpl.class);

    private final Map<String, DomainState> domainStates = new ConcurrentHashMap<>();

    @Override
    public DomainState getDomainState(String domainId) {
        // Trace level as this might be called frequently by health checks
        logger.trace("[Domain: {}] Retrieved domain state", domainId);
        return domainStates.get(domainId);
    }

    @Override
    public void initPluginSync(String domainId, String pluginId, String pluginType) {
        if (domainId == null) {
            logger.warn("Received initPluginSync for null domainId. Plugin: {}, Type: {}", pluginId, pluginType);
            return;
        }

        logger.info("[Domain: {}] Initializing synchronization for Plugin: {} [{}]", domainId, pluginId, pluginType);
        domainStates.computeIfAbsent(domainId, k -> {
            logger.debug("[Domain: {}] Creating new DomainState during initPluginSync", domainId);
            return new DomainState();
        })
        .initPluginSync(pluginId, pluginType);

        updateDomainStatus(domainId, DomainState.Status.INITIALIZING);
    }

    @Override
    public void pluginLoaded(String domainId, String pluginId) {
        if (domainId == null) {
            logger.warn("Received pluginLoaded for null domainId. Plugin: {}", pluginId);
            return;
        }

        DomainState domainState = domainStates.get(domainId);
        if (domainState == null) {
            logger.warn("[Domain: {}] Plugin {} loaded, but DomainState does not exist.", domainId, pluginId);
            return;
        }

        if (!domainState.getCreationState().containsKey(pluginId)) {
            logger.warn("[Domain: {}] Plugin {} loaded, but plugin was not marked for initialization.", domainId, pluginId);
            return;
        }

        String pluginType = domainState.getCreationState().get(pluginId).getType();
        logger.info("[Domain: {}] Plugin Loaded: {} [{}]", domainId, pluginId, pluginType);

        updatePluginStatus(domainId, pluginId, true, null);
    }

    @Override
    public void pluginFailed(String domainId, String pluginId, String message) {
        if (domainId == null) {
            logger.error("Received pluginFailed for null domainId. Plugin: {}, Error: {}", pluginId, message);
            return;
        }

        DomainState domainState = domainStates.get(domainId);
        if (domainState == null) {
            logger.error("[Domain: {}] Plugin {} failed to load, but DomainState is missing. Error: {}", domainId, pluginId, message);
            return;
        }

        if (!domainState.getCreationState().containsKey(pluginId)) {
            logger.error("[Domain: {}] Plugin {} failed to load, but was not initialized. Error: {}", domainId, pluginId, message);
            return;
        }

        String pluginType = domainState.getCreationState().get(pluginId).getType();
        logger.error("[Domain: {}] Plugin Failed: {} [{}] - Error: {}", domainId, pluginId, pluginType, message);

        updatePluginStatus(domainId, pluginId, false, message);
    }

    @Override
    public void pluginUnloaded(String domainId, String pluginId) {
        if (domainId == null) {
            logger.warn("Received pluginUnloaded for null domainId. Plugin: {}", pluginId);
            return;
        }

        DomainState domainState = domainStates.get(domainId);
        String pluginType = "Unknown";

        if (domainState != null && domainState.getCreationState().containsKey(pluginId)) {
            pluginType = domainState.getCreationState().get(pluginId).getType();
        }

        logger.info("[Domain: {}] Unloading Plugin: {} [{}]", domainId, pluginId, pluginType);

        domainStates.compute(domainId, (key, state) -> {
            if (state == null) {
                logger.debug("[Domain: {}] Attempted to unload plugin {}, but DomainState was null. Creating new state to safely remove.", domainId, pluginId);
                state = new DomainState();
            }
            state.removePlugin(pluginId);

            return state;
        });
    }

    @Override
    public void updateDomainStatus(String domainId, DomainState.Status status) {
        if (domainId == null) {
            logger.warn("Received updateDomainStatus for null domainId. Status: {}", status);
            return;
        }

        logger.info("[Domain: {}] Updating Status to: {}", domainId, status);

        DomainState domainState = domainStates.get(domainId);
        if (domainState != null && domainState.getStatus() == status) {
            logger.debug("[Domain: {}] DomainState already in desired state. Skipping update.", domainId);
            return;
        }

        domainStates.computeIfAbsent(domainId, k -> {
            logger.debug("[Domain: {}] Creating new DomainState during updateDomainStatus", domainId);
            return new DomainState();
        }).setStatus(status).setLastSync(System.currentTimeMillis());
    }

    @Override
    public void removeDomain(String domainId) {
        if (domainId == null) {
            logger.warn("Received removeDomain for null domainId");
            return;
        }

        logger.info("[Domain: {}] Removing domain state", domainId);
        DomainState removed = domainStates.remove(domainId);

        if (removed == null) {
            logger.debug("[Domain: {}] removeDomain called, but domain was not present in map.", domainId);
        }
    }

    @Override
    public boolean isAllDomainsReady() {
        if (domainStates.isEmpty()) {
            logger.trace("isAllDomainsReady: True (No domains registered)");
            return true;
        }

        boolean allReady = domainStates.values().stream().allMatch(ds -> ds.isStable() && ds.isSynchronized());

        if (!allReady && logger.isDebugEnabled()) {
            // If not ready, log exactly which domains are holding us back
            String unstableDomains = domainStates.entrySet().stream()
                    .filter(entry -> !entry.getValue().isStable() || !entry.getValue().isSynchronized())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining(", "));

            logger.debug("isAllDomainsReady: False. Unstable/Unsynchronized domains: [{}]", unstableDomains);
        } else {
            logger.trace("isAllDomainsReady: {}", allReady);
        }

        return allReady;
    }

    @Override
    public Map<String, DomainState> getDomainStates() {
        return Collections.unmodifiableMap(domainStates);
    }

    private void updatePluginStatus(String domainId, String pluginId, boolean success, String message) {
        logger.debug("[Domain: {}] Internal updatePluginStatus -> Plugin: {}, Success: {}, Message: {}", domainId, pluginId, success, message);
        domainStates.computeIfAbsent(domainId, k -> new DomainState()).updatePluginState(pluginId, success, message);
        updateDomainStatus(domainId, success ? DomainState.Status.DEPLOYED : DomainState.Status.FAILURE);
    }
}