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
package io.gravitee.am.model.oidc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(title = "Device Flow settings", description = "RFC 8628 device authorization grant configuration for the domain.")
public class DeviceFlowSettings {

    public static final int DEFAULT_DEVICE_CODE_EXPIRY_IN_SEC = 600;
    public static final int DEFAULT_POLLING_INTERVAL_IN_SEC = 5;

    @Schema(description = "Whether the device authorization grant is enabled for the domain.", defaultValue = "false")
    private boolean enabled;

    @Schema(description = "Validity, in seconds, of an issued device code and user code.", defaultValue = "600")
    private int deviceCodeExpiry = DEFAULT_DEVICE_CODE_EXPIRY_IN_SEC;

    @Schema(description = "Minimum delay, in seconds, between two polls of the token endpoint with the same device code.", defaultValue = "5")
    private int pollingInterval = DEFAULT_POLLING_INTERVAL_IN_SEC;

    public DeviceFlowSettings() {
    }

    public DeviceFlowSettings(DeviceFlowSettings other) {
        this.enabled = other.enabled;
        this.deviceCodeExpiry = other.deviceCodeExpiry;
        this.pollingInterval = other.pollingInterval;
    }

    public static DeviceFlowSettings defaultSettings() {
        return new DeviceFlowSettings();
    }
}
