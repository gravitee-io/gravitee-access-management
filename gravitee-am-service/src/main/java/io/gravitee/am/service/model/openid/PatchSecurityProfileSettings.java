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
package io.gravitee.am.service.model.openid;

import io.gravitee.am.model.oidc.SecurityProfileSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;

/**
 * @@author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchSecurityProfileSettings {

    public PatchSecurityProfileSettings() {}

    /**
     * Apply the standard Financial-grade API security profile (version 1.0).
     */
    private Optional<Boolean> enablePlainFapi;
    /**
     * Apply the standard Open Banking Brasil Financial-grade API Security Profile 1.0 (version 1.0).
     */
    private Optional<Boolean> enableFapiBrazil;

    public Optional<Boolean> getEnablePlainFapi() {
        return enablePlainFapi;
    }

    public void setEnablePlainFapi(Optional<Boolean> enablePlainFapi) {
        this.enablePlainFapi = enablePlainFapi;
    }

    public Optional<Boolean> getEnableFapiBrazil() {
        return enableFapiBrazil;
    }

    public void setEnableFapiBrazil(Optional<Boolean> enableFapiBrazil) {
        this.enableFapiBrazil = enableFapiBrazil;
    }

    public SecurityProfileSettings patch(SecurityProfileSettings toPatch) {
        SecurityProfileSettings result=toPatch!=null? toPatch: SecurityProfileSettings.defaultSettings();

        SetterUtils.safeSet(result::setEnablePlainFapi, this.getEnablePlainFapi(), boolean.class);
        SetterUtils.safeSet(result::setEnableFapiBrazil, this.getEnableFapiBrazil(), boolean.class);

        return result;
    }
}
