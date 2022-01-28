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

import io.gravitee.am.model.EnrollmentSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchEnrollmentSettings {

    private Optional<Boolean> forceEnrollment;
    private Optional<Long> skipTimeSeconds;

    public PatchEnrollmentSettings() {
    }

    public EnrollmentSettings patch(EnrollmentSettings _toPatch) {
        EnrollmentSettings toPatch = _toPatch == null ? new EnrollmentSettings() : new EnrollmentSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setForceEnrollment, this.getForceEnrollment());
        final Optional<Long> skipTimeSeconds = isNull(this.getSkipTimeSeconds()) ? Optional.empty() :
                this.getSkipTimeSeconds().filter(Objects::nonNull).map(Math::abs);
        SetterUtils.safeSet(toPatch::setSkipTimeSeconds, skipTimeSeconds);
        return toPatch;
    }

    public Optional<Boolean> getForceEnrollment() {
        return forceEnrollment;
    }

    public void setForceEnrollment(Optional<Boolean> forceEnrollment) {
        this.forceEnrollment = forceEnrollment;
    }

    public Optional<Long> getSkipTimeSeconds() {
        return skipTimeSeconds;
    }

    public void setSkipTimeSeconds(Optional<Long> skipTimeSeconds) {
        this.skipTimeSeconds = skipTimeSeconds;
    }
}
