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

/**
 * @author GraviteeSource Team
 */
public interface DomainReadinessService {
    /**
     * Get detailed state of a domain.
     * @param domainId Domain ID.
     * @return DomainState object.
     */
    DomainState getDomainState(String domainId);
    
    /**
     * Initialize the synchronization status of a plugin.
     * @param domainId Domain ID.
     * @param pluginId Plugin ID.
     * @param pluginType Plugin Type.
     */
    void initPluginSync(String domainId, String pluginId, String pluginType);

    /**
     * Mark a plugin as successfully loaded/synchronized.
     * @param domainId Domain ID.
     * @param pluginId Plugin ID.
     */
    void pluginLoaded(String domainId, String pluginId);

    /**
     * Mark a plugin as failed to load/synchronize.
     * @param domainId Domain ID.
     * @param pluginId Plugin ID.
     * @param message Failure message.
     */
    void pluginFailed(String domainId, String pluginId, String message);

    /**
     * Remove a plugin from matching.
     * @param domainId Domain ID.
     * @param pluginId Plugin ID.
     */
    void pluginUnloaded(String domainId, String pluginId);

    /**
     * Update the status of a domain.
     * @param domainId Domain ID.
     * @param status Domain Status.
     */
    void updateDomainStatus(String domainId, DomainState.Status status);

    /**
     * Remove a domain from tracking.
     * @param domainId Domain ID.
     */
    void removeDomain(String domainId);

    /**
     * Check if all domains are ready (stable and synchronized).
     * @return true if all domains are ready, false otherwise.
     */
    boolean isAllDomainsReady();

    /**
     * Get all domain states.
     * @return Map of domain ID to DomainState.
     */
    java.util.Map<String, DomainState> getDomainStates();
}
