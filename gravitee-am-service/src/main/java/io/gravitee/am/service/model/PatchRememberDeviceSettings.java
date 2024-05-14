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
package io.gravitee.am.service.model;

import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchRememberDeviceSettings {

    private Optional<Boolean> active;
    private Optional<Boolean> skipRememberDevice;
    private Optional<Long> expirationTimeSeconds;
    private Optional<String> deviceIdentifierId;

    public PatchRememberDeviceSettings() {
    }

    public PatchRememberDeviceSettings(PatchRememberDeviceSettings other) {
        this.active = other.active;
        this.skipRememberDevice = other.skipRememberDevice;
        this.expirationTimeSeconds = other.expirationTimeSeconds;
        this.deviceIdentifierId = other.deviceIdentifierId;
    }

    public Optional<Boolean> getActive() {
        return active;
    }

    public Optional<Long> getExpirationTimeSeconds() {
        return expirationTimeSeconds;
    }

    public Optional<String> getDeviceIdentifierId() {
        return deviceIdentifierId;
    }

    public Optional<Boolean> getSkipRememberDevice() {
        return skipRememberDevice;
    }

    public void setActive(Optional<Boolean> active) {
        this.active = active;
    }

    public void setExpirationTimeSeconds(Optional<Long> expirationTimeSeconds) {
        this.expirationTimeSeconds = expirationTimeSeconds;
    }

    public void setDeviceIdentifierId(Optional<String> deviceIdentifierId) {
        this.deviceIdentifierId = deviceIdentifierId;
    }

    public void setSkipRememberDevice(Optional<Boolean> skipRememberDevice) {
        this.skipRememberDevice = skipRememberDevice;
    }

    public RememberDeviceSettings patch(RememberDeviceSettings _toPatch) {
        RememberDeviceSettings toPatch = _toPatch == null ? new RememberDeviceSettings() : new RememberDeviceSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setDeviceIdentifierId, this.getDeviceIdentifierId());
        SetterUtils.safeSet(toPatch::setActive, this.getActive());
        SetterUtils.safeSet(toPatch::setSkipChallengeWhenRememberDevice, this.getSkipRememberDevice());
        final Optional<Long> expirationTimeSeconds = isNull(this.getExpirationTimeSeconds()) ? Optional.empty() : this.getExpirationTimeSeconds();
        SetterUtils.safeSet(toPatch::setExpirationTimeSeconds, expirationTimeSeconds.filter(Objects::nonNull).map(Math::abs));
        return toPatch;
    }
}
