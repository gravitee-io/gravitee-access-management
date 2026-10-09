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

import io.gravitee.am.model.application.ApplicationLightweightJwtSettings;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ApplicationLightweightJwtSettingsMongo {

    private boolean enabled;
    private List<String> accessTokenAllowlist;
    private List<String> idTokenAllowlist;

    public ApplicationLightweightJwtSettings convert() {
        ApplicationLightweightJwtSettings settings = new ApplicationLightweightJwtSettings();
        settings.setEnabled(isEnabled());
        settings.setAccessTokenAllowlist(accessTokenAllowlist != null ? new ArrayList<>(accessTokenAllowlist) : new ArrayList<>());
        settings.setIdTokenAllowlist(idTokenAllowlist != null ? new ArrayList<>(idTokenAllowlist) : new ArrayList<>());
        return settings;
    }

    public static ApplicationLightweightJwtSettingsMongo convert(ApplicationLightweightJwtSettings settings) {
        if (settings == null) {
            return null;
        }
        ApplicationLightweightJwtSettingsMongo mongo = new ApplicationLightweightJwtSettingsMongo();
        mongo.setEnabled(settings.isEnabled());
        mongo.setAccessTokenAllowlist(settings.getAccessTokenAllowlist());
        mongo.setIdTokenAllowlist(settings.getIdTokenAllowlist());
        return mongo;
    }
}
