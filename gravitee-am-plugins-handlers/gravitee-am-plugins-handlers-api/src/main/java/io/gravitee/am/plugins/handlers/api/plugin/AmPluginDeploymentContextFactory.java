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
package io.gravitee.am.plugins.handlers.api.plugin;

import io.gravitee.node.api.license.LicenseManager;
import io.gravitee.plugin.api.PluginDeploymentContext;
import io.gravitee.plugin.api.PluginDeploymentContextFactory;

/**
 * Decides whether a plugin may be deployed on this node.
 * <p>
 * Self-hosted nodes keep the gravitee-node behaviour (cf. {@code NodeDeploymentContextFactory}):
 * a plugin whose manifest declares a license feature is only deployed when the node's platform
 * license grants it. Managed cloud nodes have no platform license — entitlements are
 * per-organization licenses delivered at runtime through the cockpit/cloud command flow — so every
 * plugin is deployed and license restrictions are enforced per request instead
 * (cf. {@code io.gravitee.am.service.PluginLicenseGate}).
 *
 * @author GraviteeSource Team
 */
public class AmPluginDeploymentContextFactory implements PluginDeploymentContextFactory<PluginDeploymentContext> {

    private final LicenseManager licenseManager;
    private final boolean managedCloudEnabled;

    public AmPluginDeploymentContextFactory(LicenseManager licenseManager, boolean managedCloudEnabled) {
        this.licenseManager = licenseManager;
        this.managedCloudEnabled = managedCloudEnabled;
    }

    @Override
    public PluginDeploymentContext create() {
        return feature -> managedCloudEnabled || licenseManager.getPlatformLicense().isFeatureEnabled(feature);
    }
}
