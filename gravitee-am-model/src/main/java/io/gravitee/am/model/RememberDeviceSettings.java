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

import lombok.ToString;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RememberDeviceSettings {

    private boolean active;
    private boolean skipRememberDevice;
    private Long expirationTimeSeconds;
    private String deviceIdentifierId;

    public RememberDeviceSettings() {
    }

    public RememberDeviceSettings(RememberDeviceSettings other) {
        this.active = other.active;
        this.expirationTimeSeconds = other.expirationTimeSeconds;
        this.deviceIdentifierId = other.deviceIdentifierId;
        this.skipRememberDevice = other.skipRememberDevice;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSkipRememberDevice() {
        return skipRememberDevice;
    }

    public Long getExpirationTimeSeconds() {
        return expirationTimeSeconds;
    }

    public String getDeviceIdentifierId() {
        return deviceIdentifierId;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setExpirationTimeSeconds(Long expirationTimeSeconds) {
        this.expirationTimeSeconds = expirationTimeSeconds;
    }

    public void setDeviceIdentifierId(String deviceIdentifierId) {
        this.deviceIdentifierId = deviceIdentifierId;
    }

    public void setSkipRememberDevice(boolean skipRememberDevice) {
        this.skipRememberDevice = skipRememberDevice;
    }
}
