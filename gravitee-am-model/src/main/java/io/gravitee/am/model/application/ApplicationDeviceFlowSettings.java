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
package io.gravitee.am.model.application;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.DeviceFlowSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-application override of the domain device flow timings. Absent means the application
 * inherits the domain entirely, and follows it as it changes.
 *
 * Whether the flow is enabled is not overridable: it is a domain-level switch, and an application
 * opts in by carrying the device_code grant type.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(title = "Application Device Flow settings", description = "Per-application override of the domain device code " +
        "expiry and polling interval. Absent means the application inherits the domain settings.")
public class ApplicationDeviceFlowSettings {

    @Schema(description = "Validity, in seconds, of an issued device code and user code.", defaultValue = "600")
    private int deviceCodeExpiry = DeviceFlowSettings.DEFAULT_DEVICE_CODE_EXPIRY_IN_SEC;

    @Schema(description = "Minimum delay, in seconds, between two polls of the token endpoint with the same device code.", defaultValue = "5")
    private int pollingInterval = DeviceFlowSettings.DEFAULT_POLLING_INTERVAL_IN_SEC;

    public ApplicationDeviceFlowSettings() {
    }

    public ApplicationDeviceFlowSettings(ApplicationDeviceFlowSettings other) {
        this.deviceCodeExpiry = other.deviceCodeExpiry;
        this.pollingInterval = other.pollingInterval;
    }

    public static ApplicationDeviceFlowSettings getInstance(Domain domain, Client client) {
        if (client != null && client.getDeviceFlowSettings() != null) {
            return client.getDeviceFlowSettings();
        }

        final ApplicationDeviceFlowSettings inherited = new ApplicationDeviceFlowSettings();
        final DeviceFlowSettings domainSettings = domain != null && domain.getOidc() != null
                ? domain.getOidc().getDeviceFlowSettings()
                : null;
        if (domainSettings != null) {
            inherited.setDeviceCodeExpiry(domainSettings.getDeviceCodeExpiry());
            inherited.setPollingInterval(domainSettings.getPollingInterval());
        }
        return inherited;
    }
}
