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

import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.SecurityProfileSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;

/**
 * @@author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchCIBASettings {

    public PatchCIBASettings() {}

    /**
     * true if CIBA flow is enabled for the domain
     */
    private Optional<Boolean> enabled;

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public CIBASettings patch(CIBASettings toPatch) {
        CIBASettings result=toPatch!=null? toPatch: CIBASettings.defaultSettings();

        SetterUtils.safeSet(result::setEnabled, this.getEnabled(), boolean.class);

        return result;
    }
}
