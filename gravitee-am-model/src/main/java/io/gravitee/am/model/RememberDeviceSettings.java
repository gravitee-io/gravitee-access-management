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
package io.gravitee.am.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class RememberDeviceSettings {

    private boolean active;

    /**
     * Only used with conditional MFA challenge. If true, remember device will not be applied when MFA condition evaluates to no risk
     */
    @JsonProperty("skipRememberDevice")
    private boolean isSkipChallengeWhenRememberDevice;
    private Long expirationTimeSeconds;
    private String deviceIdentifierId;

    public RememberDeviceSettings() {
    }

    public RememberDeviceSettings(RememberDeviceSettings other) {
        this.active = other.active;
        this.expirationTimeSeconds = other.expirationTimeSeconds;
        this.deviceIdentifierId = other.deviceIdentifierId;
        this.isSkipChallengeWhenRememberDevice = other.isSkipChallengeWhenRememberDevice;
    }
}
