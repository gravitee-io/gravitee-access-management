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

import io.gravitee.am.model.MFASettings;
import io.gravitee.am.service.utils.SetterUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;

import static java.util.Objects.nonNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatchMFASettings {
    private Optional<String> loginRule;
    private Optional<PatchFactorSettings> factor;
    private Optional<String> stepUpAuthenticationRule;
    private Optional<PatchStepUpAuthentication> stepUpAuthentication;
    private Optional<String> adaptiveAuthenticationRule;
    private Optional<PatchRememberDeviceSettings> rememberDevice;
    @Deprecated
    private Optional<PatchEnrollmentSettings> enrollment;
    private Optional<PatchEnrollSettings> enroll;
    private Optional<PatchChallengeSettings> challenge;
    public MFASettings patch(MFASettings _toPatch) {
        MFASettings toPatch = _toPatch == null ? new MFASettings() : new MFASettings(_toPatch);
        SetterUtils.safeSet(toPatch::setLoginRule, this.getLoginRule());

        if (nonNull(this.getFactor()) && this.getFactor().isPresent()) {
            toPatch.setFactor(this.getFactor().get().patch(toPatch.getFactor()));
        }

        SetterUtils.safeSet(toPatch::setStepUpAuthenticationRule, this.getStepUpAuthenticationRule());
        SetterUtils.safeSet(toPatch::setAdaptiveAuthenticationRule, this.getAdaptiveAuthenticationRule());

        if (nonNull(this.getStepUpAuthentication()) && this.getStepUpAuthentication().isPresent()) {
            toPatch.setStepUpAuthentication(this.getStepUpAuthentication().get().patch(toPatch.getStepUpAuthentication()));
        }

        if (nonNull(this.getRememberDevice()) && this.getRememberDevice().isPresent()) {
            toPatch.setRememberDevice(this.getRememberDevice().get().patch(toPatch.getRememberDevice()));
        }

        if (nonNull(this.getEnrollment()) && this.getEnrollment().isPresent()) {
            toPatch.setEnrollment(this.getEnrollment().get().patch(toPatch.getEnrollment()));
        }

        if (nonNull(this.getEnroll()) && this.getEnroll().isPresent()) {
            toPatch.setEnroll(this.getEnroll().get().patch(toPatch.getEnroll()));
        }

        if (nonNull(this.getChallenge()) && this.getChallenge().isPresent()) {
            toPatch.setChallenge(this.getChallenge().get().patch(toPatch.getChallenge()));
        }
        return toPatch;
    }
}
