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

import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.alert.AlertTriggerType;
import io.gravitee.am.service.utils.SetterUtils;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchAlertTrigger {

    private Optional<Boolean> enabled;

    private Optional<List<String>> alertNotifiers;

    @NotNull
    private AlertTriggerType type;


    public AlertTrigger patch(AlertTrigger _toPatch) {
        // create new object for audit purpose (patch json result)
        AlertTrigger toPatch = new AlertTrigger(_toPatch);

        SetterUtils.safeSet(toPatch::setEnabled, this.getEnabled());
        SetterUtils.safeSet(toPatch::setAlertNotifiers, this.getAlertNotifiers());

        return toPatch;
    }

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public AlertTriggerType getType() {
        return type;
    }

    public void setType(AlertTriggerType type) {
        this.type = type;
    }

    public Optional<List<String>> getAlertNotifiers() {
        return alertNotifiers;
    }

    public void setAlertNotifiers(Optional<List<String>> alertNotifiers) {
        this.alertNotifiers = alertNotifiers;
    }

    @Override
    public String toString() {
        return "PatchAlertTrigger{" +
                "enabled=" + enabled +
                ", alertNotifiers=" + alertNotifiers +
                ", type=" + type +
                '}';
    }
}
