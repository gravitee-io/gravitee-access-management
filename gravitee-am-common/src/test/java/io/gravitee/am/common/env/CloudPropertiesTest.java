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
package io.gravitee.am.common.env;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class CloudPropertiesTest {

    @Test
    void disabledByDefault() {
        assertFalse(CloudProperties.isManagedCloudEnabled(environmentWith(Map.of())));
    }

    @Test
    void cloudEnabledAloneIsNotManaged() {
        assertFalse(CloudProperties.isManagedCloudEnabled(environmentWith(Map.of("cloud.enabled", "true"))));
    }

    @Test
    void managedInstallationAloneIsNotEnough() {
        assertFalse(CloudProperties.isManagedCloudEnabled(environmentWith(Map.of("installation.type", "managed"))));
    }

    @Test
    void requiresBothCloudEnabledAndManagedInstallation() {
        assertTrue(CloudProperties.isManagedCloudEnabled(environmentWith(Map.of(
                "cloud.enabled", "true",
                "installation.type", "managed"))));
    }

    @Test
    void fallsBackToLegacyCockpitEnabled() {
        assertTrue(CloudProperties.isManagedCloudEnabled(environmentWith(Map.of(
                "cockpit.enabled", "true",
                "installation.type", "managed"))));
    }

    @Test
    void cloudEnabledTakesPrecedenceOverLegacyCockpitEnabled() {
        assertFalse(CloudProperties.isManagedCloudEnabled(environmentWith(Map.of(
                "cloud.enabled", "false",
                "cockpit.enabled", "true",
                "installation.type", "managed"))));
    }

    @Test
    void installationTypeIsCaseInsensitive() {
        assertTrue(CloudProperties.isManagedCloudEnabled(environmentWith(Map.of(
                "cloud.enabled", "true",
                "installation.type", "MANAGED"))));
    }

    @Test
    void standaloneInstallationIsNotManaged() {
        assertFalse(CloudProperties.isManagedCloudEnabled(environmentWith(Map.of(
                "cloud.enabled", "true",
                "installation.type", "standalone"))));
    }

    private static Environment environmentWith(Map<String, Object> properties) {
        final StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }
}
