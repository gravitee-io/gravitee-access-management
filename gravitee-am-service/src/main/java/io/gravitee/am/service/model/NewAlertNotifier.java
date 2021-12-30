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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertNotifier;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NewAlertNotifier {

    @NotEmpty
    private String type;

    @NotEmpty
    private String name;

    private boolean enabled;

    @NotNull
    private String configuration;

    public AlertNotifier toAlertNotifier(ReferenceType refType, String refId) {
        final AlertNotifier alertNotifier = new AlertNotifier();

        alertNotifier.setType(this.type);
        alertNotifier.setName(this.name);
        alertNotifier.setEnabled(this.enabled);
        alertNotifier.setConfiguration(configuration);
        alertNotifier.setReferenceId(refId);
        alertNotifier.setReferenceType(refType);

        return alertNotifier;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    @Override
    public String toString() {
        return "NewAlertNotifier{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", configuration='" + configuration + '\'' +
                '}';
    }

    public void setName(String name) {
        this.name = name;
    }
}
