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
package io.gravitee.am.gateway.handler.common.license;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.exception.LicenseFeatureRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Domain-scoped runtime license gate for plugin loading.
 * <p>
 * Managers call {@link #check(String, String, String)} before instantiating a plugin. When the
 * organization license of the domain does not grant the plugin's feature, the plugin is skipped
 * and recorded in the domain state as unlicensed (readiness stays healthy). Outside managed cloud
 * mode the underlying {@link PluginLicenseGate} is a no-op and every plugin loads.
 * <p>
 * Blocking is acceptable here: checks run during domain (re)deployment on deployment threads,
 * and the gate is in-memory except the first domain-to-organization resolution, which is cached.
 *
 * @author GraviteeSource Team
 */
public class DomainPluginLicenseGate {

    private static final Logger logger = LoggerFactory.getLogger(DomainPluginLicenseGate.class);

    @Autowired
    private Domain domain;

    @Autowired
    private PluginLicenseGate pluginLicenseGate;

    @Autowired
    private DomainReadinessService domainReadinessService;

    /**
     * Checks that an instance of the given plugin may be loaded for the current domain.
     * <p>
     * Note that AM entities store the plugin id in their {@code type} field, so callers
     * typically pass {@code entity.getType()} as {@code pluginId}.
     *
     * @param pluginType one of the {@link PluginLicenseGate} {@code TYPE_*} constants
     * @param pluginId the plugin identifier (the {@code type} of the configured instance)
     * @param instanceId the configured instance identifier, used to record the domain state
     * @return {@code true} when the plugin may be loaded, {@code false} when it is not licensed
     */
    public boolean check(String pluginType, String pluginId, String instanceId) {
        try {
            pluginLicenseGate.check(Reference.domain(domain.getId()), pluginType, pluginId).blockingAwait();
            return true;
        } catch (LicenseFeatureRequiredException e) {
            logger.warn("Skipping {} '{}' [{}] for domain {}: {}", pluginType, instanceId, pluginId, domain.getId(), e.getMessage());
            domainReadinessService.pluginUnlicensed(domain.getId(), instanceId, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error while checking the license for {} '{}' [{}] for domain {}; loading the plugin anyway", pluginType, instanceId, pluginId, domain.getId(), e);
            return true;
        }
    }
}
