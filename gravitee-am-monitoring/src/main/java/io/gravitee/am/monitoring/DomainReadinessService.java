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
     * @param pluginName Plugin Name (optional, can be null).
     */
    void initPluginSync(String domainId, String pluginId, String pluginName);

    /**
     * Update the status of a plugin.
     * @param domainId Domain ID.
     * @param pluginId Plugin ID.
     * @param pluginName Plugin Name (optional, can be null).
     * @param success Success flag.
     * @param message Error message (optional).
     */
    void updatePluginStatus(String domainId, String pluginId, String pluginName, boolean success, String message);

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
}
