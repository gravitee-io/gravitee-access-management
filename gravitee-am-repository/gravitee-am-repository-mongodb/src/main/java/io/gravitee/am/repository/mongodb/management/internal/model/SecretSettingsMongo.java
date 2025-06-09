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

import io.gravitee.am.model.SecretExpirationSettings;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SecretSettingsMongo {
    private boolean enabled;
    private Long expiryTimeSeconds;

    public SecretExpirationSettings toModel() {
        SecretExpirationSettings secretExpirationSettings = new SecretExpirationSettings();
        secretExpirationSettings.setEnabled(this.enabled);
        secretExpirationSettings.setExpiryTimeSeconds(this.expiryTimeSeconds);
        return secretExpirationSettings;
    }

    public static SecretSettingsMongo fromModel(SecretExpirationSettings secretExpirationSettings) {
        if (secretExpirationSettings == null) {
            return null;
        }
        SecretSettingsMongo secretSettingsMongo = new SecretSettingsMongo();
        secretSettingsMongo.setEnabled(secretExpirationSettings.getEnabled());
        secretSettingsMongo.setExpiryTimeSeconds(secretExpirationSettings.getExpiryTimeSeconds());
        return secretSettingsMongo;
    }
}
