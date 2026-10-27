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
package io.gravitee.am.management.handlers.automation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Automation API mirror of {@link io.gravitee.am.model.RememberDeviceSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationRememberDeviceSettings", title = "Remember device settings",
        description = "Remember device: skips the MFA challenge on a device already used to authenticate.")
public class AutomationRememberDeviceSettings {

    @Schema(description = "Whether remember device is enabled.", defaultValue = "false")
    private boolean active;

    @JsonProperty("skipRememberDevice")
    @Schema(name = "skipRememberDevice", description = "Only used with a conditional MFA challenge: when true, " +
            "remember device is not applied when the MFA condition evaluates to no risk.", defaultValue = "false")
    private boolean skipChallengeWhenRememberDevice;

    @Schema(description = "Duration, in seconds, for which a device is remembered.", example = "36000")
    private Long expirationTimeSeconds;

    @Schema(description = "Device identifier used to recognize devices, as id:<uuid> (device identifiers have no " +
            "Automation API endpoint).", example = "id:5e8f1c0a-2b3d-4f5e-8a9b-0c1d2e3f4a5b")
    private String deviceIdentifierId;
}
