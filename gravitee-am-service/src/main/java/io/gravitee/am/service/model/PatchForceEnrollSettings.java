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

import io.gravitee.am.model.ForceEnrollSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchForceEnrollSettings {

    private Optional<Boolean> active;
    private Optional<Long> skipTimeSeconds;

    public PatchForceEnrollSettings() {
    }

    public ForceEnrollSettings patch(ForceEnrollSettings _toPatch) {
        ForceEnrollSettings toPatch = _toPatch == null ? new ForceEnrollSettings() : new ForceEnrollSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setActive, this.getActive());
        final Optional<Long> skipTimeSeconds = isNull(this.getSkipTimeSeconds()) ? Optional.empty() :
                this.getSkipTimeSeconds().filter(Objects::nonNull).map(Math::abs);
        SetterUtils.safeSet(toPatch::setSkipTimeSeconds, skipTimeSeconds);
        return toPatch;
    }

    public Optional<Boolean> getActive() {
        return active;
    }

    public void setActive(Optional<Boolean> active) {
        this.active = active;
    }

    public Optional<Long> getSkipTimeSeconds() {
        return skipTimeSeconds;
    }

    public void setSkipTimeSeconds(Optional<Long> skipTimeSeconds) {
        this.skipTimeSeconds = skipTimeSeconds;
    }
}
