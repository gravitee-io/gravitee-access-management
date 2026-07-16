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

import io.gravitee.node.api.license.License;
import io.gravitee.node.api.license.LicenseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AmPluginDeploymentContextFactoryTest {

    private static final String FEATURE = "am-idp-http";

    @Mock
    private LicenseManager licenseManager;

    @Mock
    private License platformLicense;

    @Test
    void managedCloudNodeDeploysEveryPlugin() {
        // managed cloud nodes have no platform license; entitlements are enforced per request from the org licenses
        final var factory = new AmPluginDeploymentContextFactory(licenseManager, true);

        assertTrue(factory.create().isPluginDeployable(FEATURE));
        verifyNoInteractions(licenseManager);
    }

    @Test
    void selfHostedNodeFollowsPlatformLicense() {
        when(licenseManager.getPlatformLicense()).thenReturn(platformLicense);
        when(platformLicense.isFeatureEnabled(FEATURE)).thenReturn(false);
        final var factory = new AmPluginDeploymentContextFactory(licenseManager, false);

        assertFalse(factory.create().isPluginDeployable(FEATURE));

        when(platformLicense.isFeatureEnabled(FEATURE)).thenReturn(true);
        assertTrue(factory.create().isPluginDeployable(FEATURE));
    }
}
