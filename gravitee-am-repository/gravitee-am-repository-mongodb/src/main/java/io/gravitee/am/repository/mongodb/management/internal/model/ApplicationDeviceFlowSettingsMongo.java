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

import io.gravitee.am.model.application.ApplicationDeviceFlowSettings;
import lombok.Getter;
import lombok.Setter;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class ApplicationDeviceFlowSettingsMongo {

    private int deviceCodeExpiry;
    private int pollingInterval;

    public ApplicationDeviceFlowSettings convert() {
        ApplicationDeviceFlowSettings settings = new ApplicationDeviceFlowSettings();
        settings.setDeviceCodeExpiry(deviceCodeExpiry);
        settings.setPollingInterval(pollingInterval);
        return settings;
    }

    public static ApplicationDeviceFlowSettingsMongo convert(ApplicationDeviceFlowSettings settings) {
        if (settings == null) {
            return null;
        }
        ApplicationDeviceFlowSettingsMongo mongo = new ApplicationDeviceFlowSettingsMongo();
        mongo.setDeviceCodeExpiry(settings.getDeviceCodeExpiry());
        mongo.setPollingInterval(settings.getPollingInterval());
        return mongo;
    }
}
