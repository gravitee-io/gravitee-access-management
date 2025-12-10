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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author GraviteeSource Team
 */
public class DomainReadinessServiceImpl implements DomainReadinessService {

    private final Logger logger = LoggerFactory.getLogger(DomainReadinessServiceImpl.class);

    private final Map<String, DomainState> domainStates = new ConcurrentHashMap<>();

    @Override
    public Set<String> getUnstableDomains() {
        return domainStates.entrySet().stream()
                .filter(entry -> !entry.getValue().isStable())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isDomainStable(String domainId) {
        DomainState state = domainStates.get(domainId);
        return state != null && state.isStable();
    }

    @Override
    public boolean isDomainSynchronized(String domainId) {
        DomainState state = domainStates.get(domainId);
        return state != null && state.isSynchronized();
    }

    @Override
    public DomainState getDomainState(String domainId) {
        return domainStates.get(domainId);
    }

    @Override
    public Map<String, DomainState> getDomainStates() {
        return java.util.Collections.unmodifiableMap(domainStates);
    }

    @Override
    public void updatePluginStatus(String domainId, String pluginId, String pluginName, boolean success, String message) {
        if (domainId != null) {
            logger.debug("Updates plugin status for domain {}, plugin {}, name {}, success {}, message {}", domainId, pluginId, pluginName, success, message);
            domainStates.computeIfAbsent(domainId, k -> new DomainState()).updatePluginState(pluginId, pluginName, success, message);
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
}
