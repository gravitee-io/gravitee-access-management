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

import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.alert.AlertTriggerType;
import io.gravitee.am.service.utils.SetterUtils;

import javax.validation.constraints.NotNull;
import java.util.Optional;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchAlertNotifier {

    private Optional<String> name;

    private Optional<Boolean> enabled;

    private Optional<String> configuration;

    public AlertNotifier patch(AlertNotifier _toPatch) {
        // create new object for audit purpose (patch json result)
        AlertNotifier toPatch = new AlertNotifier(_toPatch);

        SetterUtils.safeSet(toPatch::setName, this.getName());
        SetterUtils.safeSet(toPatch::setEnabled, this.getEnabled());
        SetterUtils.safeSet(toPatch::setConfiguration, this.getConfiguration());

        return toPatch;
    }

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public Optional<String> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Optional<String> configuration) {
        this.configuration = configuration;
    }

    public Optional<String> getName() {
        return name;
    }

    public void setName(Optional<String> name) {
        this.name = name;
    }
}
