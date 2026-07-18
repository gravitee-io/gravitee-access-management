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

import io.gravitee.am.model.oidc.DPoPSettings;
import io.gravitee.am.service.utils.SetterUtils;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

/**
 * Patch model for {@link DPoPSettings}.
 *
 * @author GraviteeSource Team
 */
@NoArgsConstructor
public class PatchDPoPSettings {

    private Optional<Boolean> requireDpopForAll;

    private Optional<List<String>> dpopSigningAlgorithms;

    public Optional<Boolean> getRequireDpopForAll() {
        return requireDpopForAll;
    }

    public void setRequireDpopForAll(Optional<Boolean> requireDpopForAll) {
        this.requireDpopForAll = requireDpopForAll;
    }

    public Optional<List<String>> getDpopSigningAlgorithms() {
        return dpopSigningAlgorithms;
    }

    public void setDpopSigningAlgorithms(Optional<List<String>> dpopSigningAlgorithms) {
        this.dpopSigningAlgorithms = dpopSigningAlgorithms;
    }

    public DPoPSettings patch(DPoPSettings toPatch) {
        DPoPSettings result = toPatch == null ? DPoPSettings.defaultSettings() : new DPoPSettings(toPatch);

        SetterUtils.safeSet(result::setRequireDpopForAll, this.getRequireDpopForAll(), boolean.class);
        SetterUtils.safeSet(result::setDpopSigningAlgorithms, this.getDpopSigningAlgorithms());

        return result;
    }
}
