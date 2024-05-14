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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.RememberDeviceSettings;

import static java.lang.Boolean.TRUE;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RememberDeviceSettingsMongo {

    private Boolean active;
    private boolean skipRememberDevice;
    private Long expirationTimeSeconds;
    private String deviceIdentifierId;

    public RememberDeviceSettingsMongo() {
    }

    public RememberDeviceSettingsMongo(RememberDeviceSettingsMongo other) {
        this.active = other.active;
        this.skipRememberDevice = other.skipRememberDevice;
        this.expirationTimeSeconds = other.expirationTimeSeconds;
        this.deviceIdentifierId = other.deviceIdentifierId;
    }

    public Boolean isActive() {
        return active;
    }

    public Long getExpirationTimeSeconds() {
        return expirationTimeSeconds;
    }

    public String getDeviceIdentifierId() {
        return deviceIdentifierId;
    }

    public void setActive(Boolean active) {
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

    public boolean isSkipRememberDevice() {
        return skipRememberDevice;
    }

    public RememberDeviceSettings convert() {
        var rememberDeviceSettings = new RememberDeviceSettings();
        rememberDeviceSettings.setActive(TRUE.equals(isActive()));
        rememberDeviceSettings.setSkipChallengeWhenRememberDevice(TRUE.equals(isSkipRememberDevice()));
        rememberDeviceSettings.setDeviceIdentifierId(deviceIdentifierId);
        rememberDeviceSettings.setExpirationTimeSeconds(expirationTimeSeconds);
        return rememberDeviceSettings;
    }

    public static RememberDeviceSettingsMongo convert(RememberDeviceSettings rememberDevice) {
        var rememberDeviceSettingsMongo = new RememberDeviceSettingsMongo();
        rememberDeviceSettingsMongo.setActive(TRUE.equals(rememberDevice.isActive()));
        rememberDeviceSettingsMongo.setSkipRememberDevice(TRUE.equals(rememberDevice.isSkipChallengeWhenRememberDevice()));
        rememberDeviceSettingsMongo.setDeviceIdentifierId(rememberDevice.getDeviceIdentifierId());
        rememberDeviceSettingsMongo.setExpirationTimeSeconds(rememberDevice.getExpirationTimeSeconds());
        return rememberDeviceSettingsMongo;
    }
}
